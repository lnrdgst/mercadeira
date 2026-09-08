package com.mercadeira.api.compra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
class ItemCompraV7IntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ParticipanteCompraRepository participanteCompraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void itemDaPreparacaoPermaneceSemAutoriaDeAdicaoDuranteCompra() {
        Contexto contexto = criarContexto();
        ItemCompra item = itemCompraRepository.saveAndFlush(ItemCompra.criarDaPreparacao(
                contexto.compra(), contexto.itemLista()));

        assertThat(item.isAdicionadoDuranteCompra()).isFalse();
        assertThat(item.getItemListaOrigem()).isEqualTo(contexto.itemLista());
        assertThat(item.getAdicionadoPorParticipanteCompra()).isNull();
        assertThat(item.getAdicionadoEm()).isNull();
    }

    @Test
    void schemaPermiteItemAdicionadoDuranteCompraComParticipanteEInstante() {
        Contexto contexto = criarContexto();
        UUID itemId = UUID.randomUUID();
        Instant adicionadoEm = Instant.parse("2026-09-08T20:00:00Z");

        inserirItemDuranteCompra(itemId, contexto, contexto.participanteCompra().getId(), adicionadoEm);
        entityManager.clear();

        ItemCompra item = itemCompraRepository.findById(itemId).orElseThrow();
        assertThat(item.isAdicionadoDuranteCompra()).isTrue();
        assertThat(item.getItemListaOrigem()).isNull();
        assertThat(item.getAdicionadoPorParticipanteCompra().getId()).isEqualTo(contexto.participanteCompra().getId());
        assertThat(item.getAdicionadoEm()).isEqualTo(adicionadoEm);
    }

    @Test
    void schemaRejeitaItemDuranteCompraSemParticipanteResponsavel() {
        Contexto contexto = criarContexto();

        assertThatThrownBy(() -> inserirItemDuranteCompra(
                UUID.randomUUID(), contexto, null, Instant.parse("2026-09-08T20:00:00Z")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schemaRejeitaItemDuranteCompraSemInstanteDeAdicao() {
        Contexto contexto = criarContexto();

        assertThatThrownBy(() -> inserirItemDuranteCompra(
                UUID.randomUUID(), contexto, contexto.participanteCompra().getId(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schemaRejeitaItemDaPreparacaoComAutoriaDeAdicaoDuranteCompra() {
        Contexto contexto = criarContexto();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into item_compra (
                    id, compra_id, item_lista_origem_id, adicionado_durante_compra,
                    adicionado_por_participante_compra_id, adicionado_em, ordem_exibicao,
                    descricao_snapshot, status
                ) values (?, ?, ?, false, ?, ?, ?, ?, 'PENDENTE')
                """,
                UUID.randomUUID(), contexto.compra().getId(), contexto.itemLista().getId(),
                contexto.participanteCompra().getId(), Timestamp.from(Instant.parse("2026-09-08T20:00:00Z")), 1, "Arroz"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserirItemDuranteCompra(UUID itemId, Contexto contexto, UUID participanteCompraId, Instant adicionadoEm) {
        jdbcTemplate.update("""
                insert into item_compra (
                    id, compra_id, item_lista_origem_id, adicionado_durante_compra,
                    adicionado_por_participante_compra_id, adicionado_em, ordem_exibicao,
                    descricao_snapshot, status
                ) values (?, ?, null, true, ?, ?, ?, ?, 'PENDENTE')
                """,
                itemId, contexto.compra().getId(), participanteCompraId,
                adicionadoEm == null ? null : Timestamp.from(adicionadoEm), 1, "Produto avulso");
    }

    private Contexto criarContexto() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Ana", "ana-" + UUID.randomUUID() + "@test.local", "hash", agora));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia Teste", UUID.randomUUID().toString().replace("-", ""), usuario, agora));
        MembroFamilia membro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarAdministrador(familia, usuario, agora));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista", CategoriaCompra.SUPERMERCADO, null, membro, agora));
        Compra compra = compraRepository.saveAndFlush(Compra.iniciar(lista, membro, agora));
        ParticipanteCompra participanteCompra = participanteCompraRepository.saveAndFlush(
                ParticipanteCompra.criarDireto(compra, membro, agora));
        ItemLista itemLista = itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, membro, agora));
        return new Contexto(compra, participanteCompra, itemLista);
    }

    private record Contexto(Compra compra, ParticipanteCompra participanteCompra, ItemLista itemLista) {
    }
}
