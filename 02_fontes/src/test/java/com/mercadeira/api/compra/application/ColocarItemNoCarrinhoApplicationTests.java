package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.compra.domain.TransicaoStatusItemCompraInvalidaException;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
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
class ColocarItemNoCarrinhoApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private ColocarItemNoCarrinho colocarItemNoCarrinho;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void participanteColocaItemNoCarrinhoEReplayPreservaPrimeiraAutoria() {
        Contexto contexto = criarContexto("Ana");

        ResultadoColocarItemNoCarrinho primeira = colocarItemNoCarrinho.executar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), contexto.item().getId());
        Instant marcadoEm = primeira.item().getMarcadoEm();
        ResultadoColocarItemNoCarrinho replay = colocarItemNoCarrinho.executar(
                contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), contexto.item().getId());

        assertThat(primeira.alterado()).isTrue();
        assertThat(primeira.item().getStatus()).isEqualTo(StatusItemCompra.NO_CARRINHO);
        assertThat(primeira.item().getMarcadoPorMembroFamilia().getId()).isEqualTo(contexto.membro().getId());
        assertThat(marcadoEm).isNotNull();
        assertThat(replay.alterado()).isFalse();
        assertThat(replay.item().getMarcadoPorMembroFamilia().getId()).isEqualTo(contexto.membro().getId());
        assertThat(replay.item().getMarcadoEm()).isEqualTo(marcadoEm);
        assertThat(itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(contexto.compraId())).hasSize(1);
    }

    @Test
    void rejeitaObservadorEAdministradorNaoParticipante() {
        Contexto contexto = criarContexto("Ana");
        Usuario observador = criarUsuario("Bia");
        MembroFamilia membroObservador = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarMembro(contexto.familia(), observador, agora()));
        Usuario administrador = criarUsuario("Caio");
        MembroFamilia membroAdministrador = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarAdministrador(contexto.familia(), administrador, agora()));

        assertThatThrownBy(() -> colocarItemNoCarrinho.executar(observador.getId(), contexto.familia().getId(), contexto.lista().getId(), contexto.item().getId()))
                .isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        assertThatThrownBy(() -> colocarItemNoCarrinho.executar(administrador.getId(), contexto.familia().getId(), contexto.lista().getId(), contexto.item().getId()))
                .isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        assertThat(membroObservador).isNotNull();
        assertThat(membroAdministrador).isNotNull();
    }

    @Test
    void rejeitaOutraFamiliaEItemDeOutraCompra() {
        Contexto primeira = criarContexto("Ana");
        Contexto segunda = criarContexto("Bia");

        assertThatThrownBy(() -> colocarItemNoCarrinho.executar(
                primeira.usuario().getId(), segunda.familia().getId(), primeira.lista().getId(), primeira.item().getId()))
                .isInstanceOf(ListaCompraNaoEncontradaException.class);
        assertThatThrownBy(() -> colocarItemNoCarrinho.executar(
                primeira.usuario().getId(), primeira.familia().getId(), primeira.lista().getId(), segunda.item().getId()))
                .isInstanceOf(ItemCompraNaoEncontradoException.class);
    }

    @Test
    void bloqueiaCompraForaDeAndamentoEEstadosDeRemocao() {
        Contexto compraFinalizada = criarContexto("Ana");
        jdbcTemplate.update("""
                update compra set status = 'FINALIZADA', finalizada_em = CURRENT_TIMESTAMP,
                    finalizada_por_participante_compra_id = (
                        select pc.id from participante_compra pc
                        where pc.compra_id = compra.id order by pc.id limit 1)
                where id = ?
                """, compraFinalizada.compraId());
        entityManager.clear();
        assertThatThrownBy(() -> colocarItemNoCarrinho.executar(compraFinalizada.usuario().getId(), compraFinalizada.familia().getId(), compraFinalizada.lista().getId(), compraFinalizada.item().getId()))
                .isInstanceOf(CompraForaDeAndamentoException.class);

        Contexto remocaoSolicitada = criarContexto("Bia");
        jdbcTemplate.update("update item_compra set status = 'REMOCAO_SOLICITADA', remocao_solicitada_por_membro_familia_id = ?, remocao_solicitada_em = now() where id = ?",
                remocaoSolicitada.membro().getId(), remocaoSolicitada.item().getId());
        entityManager.clear();
        assertThatThrownBy(() -> colocarItemNoCarrinho.executar(remocaoSolicitada.usuario().getId(), remocaoSolicitada.familia().getId(), remocaoSolicitada.lista().getId(), remocaoSolicitada.item().getId()))
                .isInstanceOf(TransicaoStatusItemCompraInvalidaException.class);

        Contexto removido = criarContexto("Caio");
        jdbcTemplate.update("update item_compra set status = 'REMOVIDO', decisao_remocao = 'APROVADA', remocao_solicitada_por_membro_familia_id = ?, remocao_solicitada_em = now(), remocao_resolvida_por_membro_familia_id = ?, remocao_resolvida_em = now() where id = ?",
                removido.membro().getId(), removido.membro().getId(), removido.item().getId());
        entityManager.clear();
        assertThatThrownBy(() -> colocarItemNoCarrinho.executar(removido.usuario().getId(), removido.familia().getId(), removido.lista().getId(), removido.item().getId()))
                .isInstanceOf(TransicaoStatusItemCompraInvalidaException.class);
    }

    private Contexto criarContexto(String nome) {
        Usuario usuario = criarUsuario(nome);
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia " + UUID.randomUUID(), UUID.randomUUID().toString().replace("-", ""), usuario, agora()));
        MembroFamilia membro = membroFamiliaRepository.saveAndFlush(MembroFamilia.criarAdministrador(familia, usuario, agora()));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista", CategoriaCompra.SUPERMERCADO, null, membro, agora()));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, membro, agora()));
        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, membro, agora()));
        iniciarCompra.iniciar(usuario.getId(), familia.getId(), lista.getId());
        UUID compraId = compraRepository.findByListaCompra_Id(lista.getId()).orElseThrow().getId();
        ItemCompra item = itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compraId).getFirst();
        return new Contexto(usuario, familia, membro, lista, compraId, item);
    }

    private Usuario criarUsuario(String nome) {
        return usuarioRepository.saveAndFlush(Usuario.criar(
                nome, nome.toLowerCase() + "-" + UUID.randomUUID() + "@test.local", "hash", agora()));
    }

    private Instant agora() {
        return Instant.parse("2026-09-08T18:00:00Z");
    }

    private record Contexto(Usuario usuario, Familia familia, MembroFamilia membro, ListaCompra lista, UUID compraId, ItemCompra item) {
    }
}
