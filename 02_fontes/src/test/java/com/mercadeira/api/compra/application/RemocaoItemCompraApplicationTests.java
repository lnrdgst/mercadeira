package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.DecisaoRemocao;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.compra.domain.TransicaoStatusItemCompraInvalidaException;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
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
class RemocaoItemCompraApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private SolicitarRemocaoItemCompra solicitarRemocao;
    @Autowired private AprovarRemocaoItemCompra aprovarRemocao;
    @Autowired private RejeitarRemocaoItemCompra rejeitarRemocao;
    @Autowired private ColocarItemNoCarrinho colocarNoCarrinho;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroRepository;
    @Autowired private ListaCompraRepository listaRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;


    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private java.time.Clock clock;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private AdicionarItemDuranteCompra adicionarItem;
    @Autowired private com.mercadeira.api.compra.repository.ParticipanteCompraRepository participanteCompraRepository;

    @org.junit.jupiter.api.BeforeEach
    void configurarClock() { tempo(0); }

    @Test
    void participanteSolicitaComAuditoriaPersistida() {
        Contexto c = noCarrinho();
        tempo(10);
        var resultado = solicitar(c.usuarioSolicitante(), c);
        assertThat(resultado.solicitacaoCriada()).isTrue();
        assertThat(resultado.removidoAutomaticamente()).isFalse();
        ItemCompra item = recarregar(c);
        assertThat(item.getStatus()).isEqualTo(StatusItemCompra.REMOCAO_SOLICITADA);
        assertThat(item.getRemocaoSolicitadaPorMembroFamilia().getId()).isEqualTo(c.membroSolicitante().getId());
        assertThat(item.getRemocaoSolicitadaEm()).isEqualTo(agora().plusSeconds(10));
        semDecisao(item);
    }

    @Test
    void replaySolicitacaoPorOutroParticipanteNaoTrocaSolicitanteNemAutoaprova() {
        Contexto c = solicitada();
        var antes = auditoria(recarregar(c));
        tempo(20);
        var replay = solicitar(c.usuarioResponsavel(), c);
        assertThat(replay.solicitacaoCriada()).isFalse();
        assertThat(replay.removidoAutomaticamente()).isFalse();
        assertThat(auditoria(recarregar(c))).isEqualTo(antes);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"aprovar,REMOVIDO,APROVADA", "rejeitar,NO_CARRINHO,REJEITADA"})
    void decisaoPreservaSolicitacaoEReplayPreservaTodaAuditoria(String acao, StatusItemCompra status, DecisaoRemocao decisao) {
        Contexto c = solicitada();
        tempo(20);
        var resultado = decidir(acao, c.usuarioResponsavel(), c);
        assertThat(resultado.decisaoAplicada()).isTrue();
        ItemCompra item = recarregar(c);
        assertThat(item.getStatus()).isEqualTo(status);
        assertThat(item.getDecisaoRemocao()).isEqualTo(decisao);
        assertThat(item.getRemocaoSolicitadaPorMembroFamilia().getId()).isEqualTo(c.membroSolicitante().getId());
        assertThat(item.getRemocaoSolicitadaEm()).isEqualTo(agora().plusSeconds(10));
        assertThat(item.getRemocaoResolvidaPorMembroFamilia().getId()).isEqualTo(c.membroResponsavel().getId());
        assertThat(item.getRemocaoResolvidaEm()).isEqualTo(agora().plusSeconds(20));
        var antes = auditoria(item);
        tempo(30);
        assertThat(decidir(acao, c.usuarioResponsavel(), c).decisaoAplicada()).isFalse();
        assertThat(auditoria(recarregar(c))).isEqualTo(antes);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void autoaprovacaoSegueMesmaRegraComUmOuVariosParticipantes(boolean segundoParticipante) {
        Contexto c = criarContexto(segundoParticipante);
        colocar(c, c.usuarioResponsavel());
        tempo(10);
        var resultado = solicitar(c.usuarioResponsavel(), c);
        assertThat(resultado.solicitacaoCriada()).isTrue();
        assertThat(resultado.removidoAutomaticamente()).isTrue();
        ItemCompra item = recarregar(c);
        assertThat(item.getStatus()).isEqualTo(StatusItemCompra.REMOVIDO);
        assertThat(item.getDecisaoRemocao()).isEqualTo(DecisaoRemocao.APROVADA);
        assertThat(item.getRemocaoSolicitadaPorMembroFamilia().getId()).isEqualTo(c.membroResponsavel().getId());
        assertThat(item.getRemocaoResolvidaPorMembroFamilia().getId()).isEqualTo(c.membroResponsavel().getId());
        assertThat(item.getRemocaoSolicitadaEm()).isEqualTo(agora().plusSeconds(10));
        assertThat(item.getRemocaoResolvidaEm()).isEqualTo(agora().plusSeconds(10));
    }

    @Test
    void novoCicloAposRejeicaoSubstituiSolicitanteDataELimpaResolucao() {
        Contexto c = solicitada();
        tempo(20);
        rejeitar(c.usuarioResponsavel(), c);
        ItemCompra rejeitado = recarregar(c);
        assertThat(rejeitado.getStatus()).isEqualTo(StatusItemCompra.NO_CARRINHO);
        assertThat(rejeitado.getDecisaoRemocao()).isEqualTo(DecisaoRemocao.REJEITADA);
        assertThat(rejeitado.getRemocaoSolicitadaPorMembroFamilia().getId()).isEqualTo(c.membroSolicitante().getId());
        assertThat(rejeitado.getRemocaoSolicitadaEm()).isEqualTo(agora().plusSeconds(10));
        assertThat(rejeitado.getRemocaoResolvidaPorMembroFamilia().getId()).isEqualTo(c.membroResponsavel().getId());
        assertThat(rejeitado.getRemocaoResolvidaEm()).isEqualTo(agora().plusSeconds(20));
        Usuario terceiro = participanteAdicional(c);
        UUID terceiroId = membroRepository.findByFamilia_IdAndUsuario_Id(c.familia().getId(), terceiro.getId()).orElseThrow().getId();
        tempo(30);
        var novo = solicitar(terceiro, c);
        assertThat(novo.solicitacaoCriada()).isTrue();
        assertThat(novo.removidoAutomaticamente()).isFalse();
        ItemCompra item = recarregar(c);
        assertThat(item.getStatus()).isEqualTo(StatusItemCompra.REMOCAO_SOLICITADA);
        assertThat(item.getRemocaoSolicitadaPorMembroFamilia().getId()).isEqualTo(terceiroId);
        assertThat(item.getRemocaoSolicitadaEm()).isEqualTo(agora().plusSeconds(30));
        semDecisao(item);
    }

    @Test
    void novoCicloAposRejeicaoPodeSerAutoaprovado() {
        Contexto c = solicitada();
        tempo(20);
        rejeitar(c.usuarioResponsavel(), c);
        recarregar(c);
        tempo(30);
        assertThat(solicitar(c.usuarioResponsavel(), c).removidoAutomaticamente()).isTrue();
        ItemCompra item = recarregar(c);
        assertThat(item.getStatus()).isEqualTo(StatusItemCompra.REMOVIDO);
        assertThat(item.getDecisaoRemocao()).isEqualTo(DecisaoRemocao.APROVADA);
        assertThat(item.getRemocaoSolicitadaPorMembroFamilia().getId()).isEqualTo(c.membroResponsavel().getId());
        assertThat(item.getRemocaoResolvidaPorMembroFamilia().getId()).isEqualTo(c.membroResponsavel().getId());
        assertThat(item.getRemocaoSolicitadaEm()).isEqualTo(agora().plusSeconds(30));
        assertThat(item.getRemocaoResolvidaEm()).isEqualTo(agora().plusSeconds(30));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "solicitar,false", "solicitar,true", "aprovar,false", "aprovar,true", "rejeitar,false", "rejeitar,true"
    })
    void observadorOuAdministradorNaoParticipanteNaoPodeExecutar(String acao, boolean administrador) {
        Contexto c = solicitada();
        Usuario usuario = observador(c, administrador);
        assertThatThrownBy(() -> executar(acao, usuario, c)).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"aprovar,false", "aprovar,true", "rejeitar,false", "rejeitar,true"})
    void participanteNaoResponsavelNaoDecideMesmoSendoAdministrador(String acao, boolean administrador) {
        Contexto c = solicitada();
        if (administrador) {
            entityManager.flush();
            jdbcTemplate.update("update membro_familia set papel = 'ADMINISTRADOR' where id = ?", c.membroSolicitante().getId());
            entityManager.clear();
        }
        assertThatThrownBy(() -> executar(acao, c.usuarioSolicitante(), c)).isInstanceOf(UsuarioNaoPodeDecidirRemocaoItemCompraException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"solicitar", "aprovar", "rejeitar"})
    void compraFinalizadaRejeitaOperacaoAposDescartarContextoJpa(String acao) {
        Contexto c = solicitada();
        entityManager.flush();
        jdbcTemplate.update("update compra set status = 'FINALIZADA' where id = ?", c.compraId());
        entityManager.clear();
        assertThatThrownBy(() -> executar(acao, c.usuarioResponsavel(), c)).isInstanceOf(CompraForaDeAndamentoException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "solicitar,PENDENTE", "solicitar,REMOVIDO", "aprovar,PENDENTE", "aprovar,NO_CARRINHO",
        "aprovar,REJEITADA", "rejeitar,PENDENTE", "rejeitar,NO_CARRINHO", "rejeitar,REMOVIDO"
    })
    void estadosIncompativeisNaoSaoReplay(String acao, String estado) {
        Contexto c = criarContexto();
        if (!estado.equals("PENDENTE")) colocar(c, c.usuarioResponsavel());
        if (estado.equals("REMOVIDO")) solicitar(c.usuarioResponsavel(), c);
        if (estado.equals("REJEITADA")) {
            solicitar(c.usuarioSolicitante(), c);
            rejeitar(c.usuarioResponsavel(), c);
        }
        recarregar(c);
        Class<? extends RuntimeException> esperada = estado.equals("PENDENTE") && !acao.equals("solicitar")
                ? ResponsavelRemocaoItemCompraInvalidoException.class : TransicaoStatusItemCompraInvalidaException.class;
        assertThatThrownBy(() -> executar(acao, c.usuarioResponsavel(), c)).isExactlyInstanceOf(esperada);
    }

    @Test
    void usuarioDeOutraFamiliaNaoSolicita() {
        Contexto c = noCarrinho();
        Contexto outra = criarContexto();
        assertThatThrownBy(() -> solicitar(outra.usuarioResponsavel(), c))
                .isInstanceOf(com.mercadeira.api.lista.application.MembroFamiliaInvalidoException.class);
    }

    @Test
    void familiaIncompativelComListaNaoSolicita() {
        Contexto c = noCarrinho();
        Contexto outra = criarContexto();
        assertThatThrownBy(() -> solicitarRemocao.executar(c.usuarioSolicitante().getId(), outra.familia().getId(), c.lista().getId(), c.itemId()))
                .isInstanceOf(com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException.class);
    }

    @Test
    void itemDeOutraCompraNaoPodeSerSolicitado() {
        Contexto c = noCarrinho();
        Contexto outra = criarContexto();
        assertThatThrownBy(() -> solicitarRemocao.executar(c.usuarioSolicitante().getId(), c.familia().getId(), c.lista().getId(), outra.itemId()))
                .isInstanceOf(ItemCompraNaoEncontradoException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"aprovar", "rejeitar"})
    void responsavelHistoricoSemParticipanteCorrespondenteNaoPermiteFallback(String acao) {
        Contexto c = solicitada();
        Usuario estranho = observador(c, false);
        UUID membroId = membroRepository.findByFamilia_IdAndUsuario_Id(c.familia().getId(), estranho.getId()).orElseThrow().getId();
        entityManager.flush();
        jdbcTemplate.update("update item_compra set marcado_por_membro_familia_id = ? where id = ?", membroId, c.itemId());
        entityManager.clear();
        assertThatThrownBy(() -> executar(acao, c.usuarioSolicitante(), c)).isInstanceOf(ResponsavelRemocaoItemCompraInvalidoException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void transicoesPreservamAutoriaDeInclusaoEColocacao(boolean adicionadoDuranteCompra) {
        Contexto original = criarContexto();
        Contexto c = original;
        if (adicionadoDuranteCompra) {
            tempo(5);
            ItemCompra novo = adicionarItem.executar(original.usuarioSolicitante().getId(), original.familia().getId(), original.lista().getId(),
                    new AdicionarItemDuranteCompraCommand("Leite", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null));
            c = new Contexto(original.usuarioResponsavel(), original.usuarioSolicitante(), original.familia(),
                    original.membroResponsavel(), original.membroSolicitante(), original.lista(), original.compraId(), novo.getId());
        }
        tempo(10);
        colocar(c, c.usuarioResponsavel());
        Historico antes = historico(recarregar(c));
        assertThat(antes.marcadoPor()).isEqualTo(c.membroResponsavel().getId());
        assertThat(antes.marcadoEm()).isEqualTo(agora().plusSeconds(10));
        if (adicionadoDuranteCompra) {
            assertThat(antes.adicionadoPor()).isNotNull();
            assertThat(antes.adicionadoEm()).isEqualTo(agora().plusSeconds(5));
        } else {
            assertThat(antes.adicionadoPor()).isNull();
            assertThat(antes.adicionadoEm()).isNull();
        }
        tempo(20);
        solicitar(c.usuarioSolicitante(), c);
        assertThat(historico(recarregar(c))).isEqualTo(antes);
        tempo(30);
        rejeitar(c.usuarioResponsavel(), c);
        assertThat(historico(recarregar(c))).isEqualTo(antes);
        tempo(40);
        solicitar(c.usuarioSolicitante(), c);
        assertThat(historico(recarregar(c))).isEqualTo(antes);
        tempo(50);
        aprovar(c.usuarioResponsavel(), c);
        assertThat(historico(recarregar(c))).isEqualTo(antes);
    }

    private void tempo(int segundos) {
        org.mockito.Mockito.when(clock.instant()).thenReturn(agora().plusSeconds(segundos));
    }

    // Flush checks physical constraints; clear forces assertions to read PostgreSQL.
    private ItemCompra recarregar(Contexto c) {
        entityManager.flush();
        entityManager.clear();
        return itemCompraRepository.findById(c.itemId()).orElseThrow();
    }

    private Contexto noCarrinho() {
        Contexto c = criarContexto();
        colocar(c, c.usuarioResponsavel());
        return c;
    }

    private Contexto solicitada() {
        Contexto c = noCarrinho();
        tempo(10);
        solicitar(c.usuarioSolicitante(), c);
        recarregar(c);
        return c;
    }

    private ResultadoDecisaoRemocaoItemCompra decidir(String acao, Usuario usuario, Contexto c) {
        return acao.equals("aprovar") ? aprovar(usuario, c) : rejeitar(usuario, c);
    }

    private void executar(String acao, Usuario usuario, Contexto c) {
        if (acao.equals("solicitar")) solicitar(usuario, c);
        else decidir(acao, usuario, c);
    }

    private void semDecisao(ItemCompra item) {
        assertThat(item.getDecisaoRemocao()).isNull();
        assertThat(item.getRemocaoResolvidaPorMembroFamilia()).isNull();
        assertThat(item.getRemocaoResolvidaEm()).isNull();
    }

    private Usuario observador(Contexto c, boolean administrador) {
        Usuario usuario = criarUsuario("Observador");
        membroRepository.saveAndFlush(administrador
                ? MembroFamilia.criarAdministrador(c.familia(), usuario, agora())
                : MembroFamilia.criarMembro(c.familia(), usuario, agora()));
        return usuario;
    }

    private Usuario participanteAdicional(Contexto c) {
        Usuario usuario = observador(c, false);
        MembroFamilia membro = membroRepository.findByFamilia_IdAndUsuario_Id(c.familia().getId(), usuario.getId()).orElseThrow();
        participanteCompraRepository.saveAndFlush(com.mercadeira.api.compra.domain.ParticipanteCompra.criarDireto(
                compraRepository.findById(c.compraId()).orElseThrow(), membro, agora()));
        return usuario;
    }

    // Immutable audit values avoid treating managed entities as historical snapshots.
    private Auditoria auditoria(ItemCompra item) {
        return new Auditoria(item.getStatus(), item.getDecisaoRemocao(),
                item.getRemocaoSolicitadaPorMembroFamilia().getId(), item.getRemocaoSolicitadaEm(),
                item.getRemocaoResolvidaPorMembroFamilia() == null ? null : item.getRemocaoResolvidaPorMembroFamilia().getId(),
                item.getRemocaoResolvidaEm());
    }

    private Historico historico(ItemCompra item) {
        return new Historico(item.getMarcadoPorMembroFamilia().getId(), item.getMarcadoEm(),
                item.getAdicionadoPorParticipanteCompra() == null ? null : item.getAdicionadoPorParticipanteCompra().getId(),
                item.getAdicionadoEm());
    }

    private record Auditoria(StatusItemCompra status, DecisaoRemocao decisao, UUID solicitante,
            Instant solicitadaEm, UUID decisor, Instant resolvidaEm) {}
    private record Historico(UUID marcadoPor, Instant marcadoEm, UUID adicionadoPor, Instant adicionadoEm) {}

    private void colocar(Contexto contexto, Usuario usuario) {
        colocarNoCarrinho.executar(usuario.getId(), contexto.familia().getId(), contexto.lista().getId(), contexto.itemId());
    }

    private ResultadoSolicitarRemocaoItemCompra solicitar(Usuario usuario, Contexto contexto) {
        return solicitarRemocao.executar(usuario.getId(), contexto.familia().getId(), contexto.lista().getId(), contexto.itemId());
    }

    private ResultadoDecisaoRemocaoItemCompra aprovar(Usuario usuario, Contexto contexto) {
        return aprovarRemocao.executar(usuario.getId(), contexto.familia().getId(), contexto.lista().getId(), contexto.itemId());
    }

    private ResultadoDecisaoRemocaoItemCompra rejeitar(Usuario usuario, Contexto contexto) {
        return rejeitarRemocao.executar(usuario.getId(), contexto.familia().getId(), contexto.lista().getId(), contexto.itemId());
    }

    private Contexto criarContexto() {
        return criarContexto(true);
    }

    private Contexto criarContexto(boolean incluirSegundoParticipante) {
        Usuario responsavel = criarUsuario("Ana");
        Familia familia = familiaRepository.saveAndFlush(Familia.criar("Familia " + UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""), responsavel, agora()));
        MembroFamilia membroResponsavel = membroRepository.saveAndFlush(MembroFamilia.criarAdministrador(familia, responsavel, agora()));
        ListaCompra lista = listaRepository.saveAndFlush(ListaCompra.criar(familia, "Lista", CategoriaCompra.OUTROS, null, membroResponsavel, agora()));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, membroResponsavel, agora()));
        Usuario solicitante = incluirSegundoParticipante ? criarUsuario("Bia") : responsavel;
        MembroFamilia membroSolicitante = incluirSegundoParticipante
                ? membroRepository.saveAndFlush(MembroFamilia.criarMembro(familia, solicitante, agora()))
                : membroResponsavel;
        if (incluirSegundoParticipante) {
            participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, membroSolicitante, agora()));
        }
        itemListaRepository.saveAndFlush(ItemLista.criar(lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, membroResponsavel, agora()));
        UUID compraId = iniciarCompra.iniciar(responsavel.getId(), familia.getId(), lista.getId()).compra().getId();
        UUID itemId = itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compraId).getFirst().getId();
        return new Contexto(responsavel, solicitante, familia, membroResponsavel, membroSolicitante, lista, compraId, itemId);
    }

    private Usuario criarUsuario(String nome) {
        return usuarioRepository.saveAndFlush(Usuario.criar(nome, nome.toLowerCase() + UUID.randomUUID() + "@test.local", "hash", agora()));
    }

    private Instant agora() { return Instant.parse("2026-09-08T18:00:00Z"); }

    private record Contexto(Usuario usuarioResponsavel, Usuario usuarioSolicitante, Familia familia,
            MembroFamilia membroResponsavel, MembroFamilia membroSolicitante, ListaCompra lista,
            UUID compraId, UUID itemId) {
    }
}
