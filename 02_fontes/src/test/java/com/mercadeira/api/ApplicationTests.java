package com.mercadeira.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.mercadeira.api.familia.application.AprovarSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.familia.application.ListarFamiliasAtivasUsuario;
import com.mercadeira.api.familia.application.SolicitanteJaPossuiVinculoAtivoException;
import com.mercadeira.api.familia.application.SolicitarEntradaFamiliaPorCodigo;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@Transactional
@Rollback
class ApplicationTests {

    private static final String JWT_TEST_SECRET = Base64.getEncoder().encodeToString(
            "segredo-exclusivo-de-teste-com-32-bytes-ou-mais".getBytes(StandardCharsets.UTF_8));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> JWT_TEST_SECRET);
    }

    @Autowired private CadastrarUsuario cadastrarUsuario;
    @Autowired private CriarFamilia criarFamilia;
    @Autowired private SolicitarEntradaFamiliaPorCodigo solicitarEntrada;
    @Autowired private AprovarSolicitacaoEntradaFamilia aprovarSolicitacao;
    @Autowired private ListarFamiliasAtivasUsuario listarFamilias;
    @Autowired private MembroFamiliaRepository membroRepository;
    @Autowired private SolicitacaoEntradaFamiliaRepository solicitacaoRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void contextLoads() {
    }

    @Test
    void usuarioPodeTerVinculosAtivosEmMultiplasFamilias() {
        Usuario usuario = usuario("Ana");
        Familia familiaA = criarFamilia.criar(usuario.getId(), "Casa Ana");
        Familia familiaB = criarFamilia.criar(usuario.getId(), "Casa Pais");

        assertThat(listarFamilias.listar(usuario.getId()))
                .extracting(membro -> membro.getFamilia().getId())
                .containsExactly(familiaA.getId(), familiaB.getId());
    }

    @Test
    void criarFamiliaNaoCancelaPendenciasExistentes() {
        Usuario admin = usuario("Admin");
        Familia familiaDestino = criarFamilia.criar(admin.getId(), "Destino");
        Usuario usuario = usuario("Bia");
        SolicitacaoEntradaFamilia pendencia = solicitarEntrada.solicitar(usuario.getId(), familiaDestino.getCodigoIngresso());

        criarFamilia.criar(usuario.getId(), "Nova familia");

        assertThat(pendencia.getStatus()).isEqualTo(StatusSolicitacaoEntradaFamilia.PENDENTE);
    }

    @Test
    void permiteSolicitacaoEmOutraFamiliaMasImpedeNaMesmaFamiliaAtiva() {
        Usuario usuario = usuario("Bia");
        Familia familiaPropria = criarFamilia.criar(usuario.getId(), "Casa Bia");
        Usuario adminA = usuario("Admin A");
        Familia familiaA = criarFamilia.criar(adminA.getId(), "Familia A");

        assertThat(solicitarEntrada.solicitar(usuario.getId(), familiaA.getCodigoIngresso()).getStatus())
                .isEqualTo(StatusSolicitacaoEntradaFamilia.PENDENTE);
        assertThatThrownBy(() -> solicitarEntrada.solicitar(usuario.getId(), familiaPropria.getCodigoIngresso()))
                .isInstanceOf(SolicitanteJaPossuiVinculoAtivoException.class);
    }

    @Test
    void aprovacaoNaoCancelaPendenciaDeOutraFamilia() {
        Usuario adminA = usuario("Admin A");
        Familia familiaA = criarFamilia.criar(adminA.getId(), "Familia A");
        Usuario adminB = usuario("Admin B");
        Familia familiaB = criarFamilia.criar(adminB.getId(), "Familia B");
        Usuario solicitante = usuario("Bia");
        SolicitacaoEntradaFamilia solicitacaoA = solicitarEntrada.solicitar(solicitante.getId(), familiaA.getCodigoIngresso());
        SolicitacaoEntradaFamilia solicitacaoB = solicitarEntrada.solicitar(solicitante.getId(), familiaB.getCodigoIngresso());

        aprovarSolicitacao.aprovar(familiaA.getId(), solicitacaoA.getId(), membroAtivo(familiaA, adminA).getId());

        assertThat(solicitacaoA.getStatus()).isEqualTo(StatusSolicitacaoEntradaFamilia.APROVADA);
        assertThat(solicitacaoB.getStatus()).isEqualTo(StatusSolicitacaoEntradaFamilia.PENDENTE);
        assertThat(membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaA.getId(), solicitante.getId(), StatusMembroFamilia.ATIVO)).isPresent();
    }

    @Test
    void aprovarReingressoReativaMesmoVinculoSemDuplicar() {
        Usuario admin = usuario("Admin");
        Familia familia = criarFamilia.criar(admin.getId(), "Familia");
        Usuario solicitante = usuario("Bia");
        UUID membroInativoId = UUID.randomUUID();
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'ADMINISTRADOR', 'INATIVO', now(), now())",
                membroInativoId, familia.getId(), solicitante.getId());
        entityManager.clear();
        SolicitacaoEntradaFamilia solicitacao = solicitarEntrada.solicitar(solicitante.getId(), familia.getCodigoIngresso());

        aprovarSolicitacao.aprovar(familia.getId(), solicitacao.getId(), membroAtivo(familia, admin).getId());

        MembroFamilia reativado = membroRepository.findByFamilia_IdAndUsuario_Id(familia.getId(), solicitante.getId()).orElseThrow();
        assertThat(reativado.getId()).isEqualTo(membroInativoId);
        assertThat(reativado.getStatus()).isEqualTo(StatusMembroFamilia.ATIVO);
        assertThat(reativado.getPapel()).isEqualTo(PapelMembroFamilia.MEMBRO);
    }

    private Usuario usuario(String nome) {
        return cadastrarUsuario.cadastrar(nome, nome.toLowerCase() + UUID.randomUUID() + "@test.local", "senha-original");
    }

    private MembroFamilia membroAtivo(Familia familia, Usuario usuario) {
        return membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familia.getId(), usuario.getId(), StatusMembroFamilia.ATIVO).orElseThrow();
    }
}
