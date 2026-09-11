package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.StatusListaCompra;
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
class AdicionarItemDuranteCompraApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private AdicionarItemDuranteCompra adicionarItemDuranteCompra;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ParticipanteCompraRepository participanteCompraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void participanteAdicionaItemExclusivoDaCompraComAutoriaESnapshots() {
        Contexto contexto = criarContexto();
        Instant antes = Instant.now();

        ItemCompra item = adicionarItemDuranteCompra.executar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarItemDuranteCompraCommand(
                        "Cafe", new BigDecimal("2.500"), UnidadeMedida.KG, "Marca C", "Torrado"));

        assertThat(item.getCompra().getId()).isEqualTo(contexto.compraId());
        assertThat(item.getItemListaOrigem()).isNull();
        assertThat(item.isAdicionadoDuranteCompra()).isTrue();
        assertThat(item.getAdicionadoPorParticipanteCompra().getId()).isEqualTo(contexto.participanteCompraId());
        assertThat(item.getAdicionadoEm()).isAfterOrEqualTo(antes);
        assertThat(item.getStatus()).isEqualTo(StatusItemCompra.PENDENTE);
        assertThat(item.getDescricaoSnapshot()).isEqualTo("Cafe");
        assertThat(item.getQuantidadeSnapshot()).isEqualByComparingTo("2.500");
        assertThat(item.getUnidadeMedidaSnapshot()).isEqualTo(UnidadeMedida.KG.name());
        assertThat(item.getMarcaSnapshot()).isEqualTo("Marca C");
        assertThat(item.getObservacoesSnapshot()).isEqualTo("Torrado");
        assertThat(item.getOrdemExibicao()).isEqualTo(3);
        assertThat(itemListaRepository.count()).isEqualTo(2);
        assertThat(listaCompraRepository.findById(contexto.lista().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusListaCompra.EM_COMPRA);
    }

    @Test
    void inclusoesSequenciaisRecebemOrdensCrescentesSemDeduplicacao() {
        Contexto contexto = criarContexto();
        AdicionarItemDuranteCompraCommand command = new AdicionarItemDuranteCompraCommand(
                "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null);

        ItemCompra primeiro = adicionarItemDuranteCompra.executar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), command);
        ItemCompra segundo = adicionarItemDuranteCompra.executar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), command);

        assertThat(primeiro.getId()).isNotEqualTo(segundo.getId());
        assertThat(primeiro.getOrdemExibicao()).isEqualTo(3);
        assertThat(segundo.getOrdemExibicao()).isEqualTo(4);
        assertThat(itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(contexto.compraId())).hasSize(4);
        assertThat(itemListaRepository.count()).isEqualTo(2);
    }

    @Test
    void rejeitaObservadorAdministradorNaoParticipanteEUsuarioDeOutraFamilia() {
        Contexto contexto = criarContexto();
        Usuario observador = criarUsuario("Bia");
        membroFamiliaRepository.saveAndFlush(MembroFamilia.criarMembro(contexto.familia(), observador, agora()));
        Usuario administrador = criarUsuario("Caio");
        membroFamiliaRepository.saveAndFlush(MembroFamilia.criarAdministrador(contexto.familia(), administrador, agora()));
        Usuario externo = criarUsuario("Dora");
        Familia outraFamilia = familiaRepository.saveAndFlush(Familia.criar(
                "Outra familia", UUID.randomUUID().toString().replace("-", ""), externo, agora()));
        membroFamiliaRepository.saveAndFlush(MembroFamilia.criarAdministrador(outraFamilia, externo, agora()));
        AdicionarItemDuranteCompraCommand command = new AdicionarItemDuranteCompraCommand(
                "Cafe", null, null, null, null);

        assertThatThrownBy(() -> adicionarItemDuranteCompra.executar(
                observador.getId(), contexto.familia().getId(), contexto.lista().getId(), command))
                .isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        assertThatThrownBy(() -> adicionarItemDuranteCompra.executar(
                administrador.getId(), contexto.familia().getId(), contexto.lista().getId(), command))
                .isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        assertThatThrownBy(() -> adicionarItemDuranteCompra.executar(
                externo.getId(), outraFamilia.getId(), contexto.lista().getId(), command))
                .isInstanceOf(ListaCompraNaoEncontradaException.class);
    }

    @Test
    void rejeitaCompraForaDeAndamentoEDescricaoInvalida() {
        Contexto contexto = criarContexto();
        AdicionarItemDuranteCompraCommand descricaoInvalida = new AdicionarItemDuranteCompraCommand(
                " ", null, null, null, null);

        assertThatThrownBy(() -> adicionarItemDuranteCompra.executar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), descricaoInvalida))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A descricao e obrigatoria.");

        jdbcTemplate.update("""
                update compra set status = 'FINALIZADA', finalizada_em = CURRENT_TIMESTAMP,
                    finalizada_por_participante_compra_id = (
                        select pc.id from participante_compra pc
                        where pc.compra_id = compra.id order by pc.id limit 1)
                where id = ?
                """, contexto.compraId());
        entityManager.clear();
        assertThatThrownBy(() -> adicionarItemDuranteCompra.executar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                new AdicionarItemDuranteCompraCommand("Cafe", null, null, null, null)))
                .isInstanceOf(CompraForaDeAndamentoException.class);
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
        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Feijao", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 2, membro, agora()));
        iniciarCompra.iniciar(usuario.getId(), familia.getId(), lista.getId());
        UUID compraId = compraRepository.findByListaCompra_Id(lista.getId()).orElseThrow().getId();
        ParticipanteCompra participante = participanteCompraRepository
                .findByCompra_IdAndMembroFamilia_Id(compraId, membro.getId()).orElseThrow();
        return new Contexto(usuario, familia, membro, lista, compraId, participante.getId());
    }

    private Usuario criarUsuario(String nome) {
        return usuarioRepository.saveAndFlush(Usuario.criar(
                nome, nome.toLowerCase() + "-" + UUID.randomUUID() + "@test.local", "hash", agora()));
    }

    private Instant agora() {
        return Instant.parse("2026-09-08T18:00:00Z");
    }

    private record Contexto(
            Usuario usuario,
            Familia familia,
            MembroFamilia membro,
            ListaCompra lista,
            UUID compraId,
            UUID participanteCompraId) {
    }
}
