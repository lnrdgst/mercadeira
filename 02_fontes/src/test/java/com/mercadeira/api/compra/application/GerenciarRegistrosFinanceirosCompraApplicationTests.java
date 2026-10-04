package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.api.CompraResponse;
import com.mercadeira.api.compra.domain.RegistroFinanceiroCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.RegistroFinanceiroCompraRepository;
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
class GerenciarRegistrosFinanceirosCompraApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private GerenciarRegistrosFinanceirosCompra gerenciarRegistros;
    @Autowired private FinalizarCompra finalizarCompra;
    @Autowired private ConsultarCompraDaLista consultarCompra;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private RegistroFinanceiroCompraRepository registroRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void permiteMultiplosRegistrosERetornaTotalDerivadoNaCompra() {
        Contexto contexto = criarContexto();

        gerenciarRegistros.adicionar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarRegistroFinanceiroCompraCommand(new BigDecimal("82.40"), " Mercado Central "));
        gerenciarRegistros.adicionar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarRegistroFinanceiroCompraCommand(new BigDecimal("17.60"), null));

        assertThat(registroRepository.findByCompra_IdOrderByCriadoEmAscIdAsc(contexto.compraId()))
                .extracting(RegistroFinanceiroCompra::getValor)
                .containsExactly(new BigDecimal("82.40"), new BigDecimal("17.60"));
        CompraResponse response = CompraResponse.from(consultarCompra.consultar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId()), contexto.usuario().getId());
        assertThat(response.registrosFinanceiros()).hasSize(2);
        assertThat(response.registrosFinanceiros().getFirst().estabelecimentoNome()).isEqualTo("Mercado Central");
        assertThat(response.totalRegistrado()).isEqualByComparingTo("100.00");
        assertThat(response.contextoUsuario().podeGerenciarRegistrosFinanceiros()).isTrue();

        assertThatThrownBy(() -> gerenciarRegistros.adicionar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarRegistroFinanceiroCompraCommand(BigDecimal.ZERO, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gerenciarRegistros.adicionar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarRegistroFinanceiroCompraCommand(new BigDecimal("-1.00"), null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void participantePodeRemoverRegistroDaPropriaCompra() {
        Contexto contexto = criarContexto();
        RegistroFinanceiroCompra registro = gerenciarRegistros.adicionar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarRegistroFinanceiroCompraCommand(new BigDecimal("10.00"), null));

        gerenciarRegistros.remover(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), registro.getId());

        assertThat(registroRepository.findByCompra_IdOrderByCriadoEmAscIdAsc(contexto.compraId())).isEmpty();
        CompraResponse response = CompraResponse.from(consultarCompra.consultar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId()), contexto.usuario().getId());
        assertThat(response.totalRegistrado()).isEqualByComparingTo("0.00");
    }

    @Test
    void permiteInclusaoAposCompraFinalizadaSemReabrirACompra() {
        Contexto contexto = criarContexto();
        jdbcTemplate.update("""
                update compra set status = 'FINALIZADA', finalizada_em = CURRENT_TIMESTAMP,
                    finalizada_por_participante_compra_id = (
                        select pc.id from participante_compra pc
                        where pc.compra_id = compra.id order by pc.id limit 1)
                where id = ?
                """, contexto.compraId());
        entityManager.clear();

        gerenciarRegistros.adicionar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarRegistroFinanceiroCompraCommand(new BigDecimal("10.00"), null));
        assertThat(registroRepository.findByCompra_IdOrderByCriadoEmAscIdAsc(contexto.compraId())).hasSize(1);
    }

    @Test
    void registraNfceEmCompraFinalizadaCalculaTotalEImpedeDuplicidadeNaMesmaCompra() {
        Contexto contexto = criarContexto();
        jdbcTemplate.update("""
                update compra set status = 'FINALIZADA', finalizada_em = CURRENT_TIMESTAMP,
                    finalizada_por_participante_compra_id = (
                        select pc.id from participante_compra pc where pc.compra_id = compra.id order by pc.id limit 1)
                where id = ?
                """, contexto.compraId());
        entityManager.clear();
        String chave = "35261012345678000123550010000012341000012345";

        gerenciarRegistros.adicionarNfce(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new BigDecimal("37.53"), null, chave, "https://sefaz.exemplo.gov.br/nfce?q=" + chave,
                "12345678000190");

        var registros = registroRepository.findByCompra_IdOrderByCriadoEmAscIdAsc(contexto.compraId());
        assertThat(registros).singleElement().satisfies(registro -> {
            assertThat(registro.getTipo().name()).isEqualTo("NFCE");
            assertThat(registro.getChaveNfce()).isEqualTo(chave);
            assertThat(registro.getValor()).isEqualByComparingTo("37.53");
        });
        assertThat(CompraResponse.from(consultarCompra.consultar(contexto.usuario().getId(), contexto.familia().getId(),
                contexto.lista().getId()), contexto.usuario().getId()).totalRegistrado()).isEqualByComparingTo("37.53");
        assertThatThrownBy(() -> gerenciarRegistros.adicionarNfce(contexto.usuario().getId(), contexto.familia().getId(),
                contexto.lista().getId(), BigDecimal.ONE, null, chave, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Este cupom já foi registrado nesta compra.");

        Contexto outraCompra = criarContexto();
        gerenciarRegistros.adicionarNfce(outraCompra.usuario().getId(), outraCompra.familia().getId(),
                outraCompra.lista().getId(), BigDecimal.ONE, null, chave, null, null);
        assertThat(registroRepository.findByCompra_IdOrderByCriadoEmAscIdAsc(outraCompra.compraId())).hasSize(1);
    }

    @Test
    void bloqueiaMembroAtivoQueNaoParticipaDaCompra() {
        Contexto contexto = criarContexto();
        Usuario observador = criarUsuario("Bia");
        membroFamiliaRepository.saveAndFlush(MembroFamilia.criarMembro(contexto.familia(), observador, agora()));

        assertThatThrownBy(() -> gerenciarRegistros.adicionar(
                observador.getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarRegistroFinanceiroCompraCommand(new BigDecimal("10.00"), null)))
                .isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
    }

    @Test
    void preservaRegistrosAoFinalizarCompraNormalmente() {
        Contexto contexto = criarContexto();
        gerenciarRegistros.adicionar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarRegistroFinanceiroCompraCommand(new BigDecimal("130.00"), "Mercado Central"));
        entityManager.flush();
        jdbcTemplate.update("update item_compra set status = 'NO_CARRINHO' where compra_id = ?", contexto.compraId());
        entityManager.clear();

        finalizarCompra.executar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId());
        entityManager.flush();
        entityManager.clear();

        CompraResponse response = CompraResponse.from(consultarCompra.consultar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId()), contexto.usuario().getId());
        assertThat(response.status().name()).isEqualTo("FINALIZADA");
        assertThat(response.registrosFinanceiros()).hasSize(1);
        assertThat(response.totalRegistrado()).isEqualByComparingTo("130.00");
        assertThat(response.contextoUsuario().podeGerenciarRegistrosFinanceiros()).isTrue();
    }

    private Contexto criarContexto() {
        Usuario usuario = criarUsuario("Ana");
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia Teste", UUID.randomUUID().toString().replace("-", ""), usuario, agora()));
        MembroFamilia membro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarAdministrador(familia, usuario, agora()));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista", CategoriaCompra.SUPERMERCADO, null, membro, agora()));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, membro, agora()));
        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, membro, agora()));
        iniciarCompra.iniciar(usuario.getId(), familia.getId(), lista.getId());
        return new Contexto(usuario, familia, lista,
                compraRepository.findByListaCompra_Id(lista.getId()).orElseThrow().getId());
    }

    private Usuario criarUsuario(String nome) {
        return usuarioRepository.saveAndFlush(Usuario.criar(
                nome, nome.toLowerCase() + "-" + UUID.randomUUID() + "@test.local", "hash", agora()));
    }

    private Instant agora() {
        return Instant.parse("2026-10-02T18:00:00Z");
    }

    private record Contexto(Usuario usuario, Familia familia, ListaCompra lista, UUID compraId) {
    }
}
