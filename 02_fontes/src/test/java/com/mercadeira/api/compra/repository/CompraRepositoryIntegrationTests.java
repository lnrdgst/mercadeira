package com.mercadeira.api.compra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
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
class CompraRepositoryIntegrationTests {

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
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ParticipanteCompraRepository participanteCompraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void localizaCompraPorIdDaListaCompra() {
        ContextoCompra contexto = criarContexto();

        Compra encontrada = compraRepository.findByListaCompra_Id(contexto.lista().getId()).orElseThrow();

        assertThat(id(encontrada)).isEqualTo(id(contexto.compra()));
    }

    @Test
    void localizaParticipantePorCompraEMembro() {
        ContextoCompra contexto = criarContexto();
        MembroFamilia membro = criarMembro(contexto.familia(), "Bia", PapelMembroFamilia.MEMBRO);
        ParticipanteCompra participante = participanteCompraRepository.saveAndFlush(
                ParticipanteCompra.criarDireto(contexto.compra(), membro, Instant.parse("2026-09-04T16:00:00Z")));

        ParticipanteCompra encontrado = participanteCompraRepository
                .findByCompra_IdAndMembroFamilia_Id(id(contexto.compra()), membro.getId())
                .orElseThrow();

        assertThat(id(encontrado)).isEqualTo(id(participante));
    }

    @Test
    void verificaExistenciaDeParticipantePorCompraEMembro() {
        ContextoCompra contexto = criarContexto();
        MembroFamilia participante = criarMembro(contexto.familia(), "Bia", PapelMembroFamilia.MEMBRO);
        MembroFamilia ausente = criarMembro(contexto.familia(), "Caio", PapelMembroFamilia.MEMBRO);
        participanteCompraRepository.saveAndFlush(
                ParticipanteCompra.criarDireto(contexto.compra(), participante, Instant.parse("2026-09-04T16:00:00Z")));

        assertThat(participanteCompraRepository.existsByCompra_IdAndMembroFamilia_Id(id(contexto.compra()), participante.getId()))
                .isTrue();
        assertThat(participanteCompraRepository.existsByCompra_IdAndMembroFamilia_Id(id(contexto.compra()), ausente.getId()))
                .isFalse();
    }

    @Test
    void listaItensDaCompraPorOrdemExibicaoEId() {
        ContextoCompra contexto = criarContexto();
        ItemCompra primeiro = criarItemCompra(contexto, "Arroz", 1);
        ItemCompra segundo = criarItemCompra(contexto, "Feijao", 2);
        ItemCompra terceiro = criarItemCompra(contexto, "Macarrao", 2);
        itemCompraRepository.saveAllAndFlush(List.of(terceiro, primeiro, segundo));

        List<ItemCompra> itens = itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(id(contexto.compra()));

        assertThat(itens).extracting(item -> (Integer) campo(item, "ordemExibicao")).containsExactly(1, 2, 2);
        List<UUID> idsComMesmaOrdem = itens.subList(1, 3).stream().map(this::id).toList();
        assertThat(idsComMesmaOrdem).isEqualTo(idsComMesmaOrdem.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList());
    }

    @Test
    void listaParticipantesDaCompraPorGeradoEmEId() {
        ContextoCompra contexto = criarContexto();
        MembroFamilia primeiroMembro = criarMembro(contexto.familia(), "Bia", PapelMembroFamilia.MEMBRO);
        MembroFamilia segundoMembro = criarMembro(contexto.familia(), "Caio", PapelMembroFamilia.MEMBRO);
        Instant geradoEm = Instant.parse("2026-09-04T16:00:00Z");
        participanteCompraRepository.saveAllAndFlush(List.of(
                ParticipanteCompra.criarDireto(contexto.compra(), segundoMembro, geradoEm),
                ParticipanteCompra.criarDireto(contexto.compra(), primeiroMembro, geradoEm)));

        List<ParticipanteCompra> participantes = participanteCompraRepository
                .findByCompra_IdOrderByGeradoEmAscIdAsc(id(contexto.compra()));

        List<UUID> ids = participantes.stream().map(this::id).toList();
        assertThat(ids).isEqualTo(ids.stream().sorted(Comparator.comparing(UUID::toString)).toList());
    }

    @Test
    void carregaListaCompraComLockPessimistaDentroDaTransacao() {
        ContextoCompra contexto = criarContexto();
        entityManager.clear();

        ListaCompra encontrada = listaCompraRepository.findByIdForUpdate(contexto.lista().getId()).orElseThrow();

        assertThat(encontrada.getId()).isEqualTo(contexto.lista().getId());
    }

    @Test
    void listaParticipantesAtivosDaListaEmOrdemDeterministica() {
        ContextoCompra contexto = criarContexto();
        MembroFamilia primeiroMembro = criarMembro(contexto.familia(), "Bia", PapelMembroFamilia.MEMBRO);
        MembroFamilia segundoMembro = criarMembro(contexto.familia(), "Caio", PapelMembroFamilia.MEMBRO);
        Instant entrouEm = Instant.parse("2026-09-04T16:00:00Z");
        ParticipanteLista removido = participanteListaRepository.saveAndFlush(
                ParticipanteLista.criar(contexto.lista(), contexto.criador(), entrouEm));
        removido.sair(Instant.parse("2026-09-04T16:01:00Z"));
        participanteListaRepository.saveAndFlush(removido);
        participanteListaRepository.saveAllAndFlush(List.of(
                ParticipanteLista.criar(contexto.lista(), segundoMembro, entrouEm),
                ParticipanteLista.criar(contexto.lista(), primeiroMembro, entrouEm)));

        List<ParticipanteLista> participantes = participanteListaRepository
                .findByListaCompra_IdAndSaiuEmIsNullOrderByEntrouEmAscIdAsc(contexto.lista().getId());

        assertThat(participantes).hasSize(2).allSatisfy(participante -> assertThat(participante.getSaiuEm()).isNull());
        List<UUID> ids = participantes.stream().map(ParticipanteLista::getId).toList();
        assertThat(ids).isEqualTo(ids.stream().sorted(Comparator.comparing(UUID::toString)).toList());
    }

    private ContextoCompra criarContexto() {
        Instant agora = Instant.parse("2026-09-04T15:00:00Z");
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar("Ana", UUID.randomUUID() + "@test.local", "hash", agora));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia Teste", UUID.randomUUID().toString().replace("-", ""), usuario, agora));
        MembroFamilia criador = membroFamiliaRepository.saveAndFlush(MembroFamilia.criarAdministrador(familia, usuario, agora));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista", CategoriaCompra.SUPERMERCADO, "Mercado", criador, agora));
        Compra compra = compraRepository.saveAndFlush(Compra.iniciar(lista, criador, agora));
        return new ContextoCompra(familia, criador, lista, compra);
    }

    private MembroFamilia criarMembro(Familia familia, String nome, PapelMembroFamilia papel) {
        Instant agora = Instant.parse("2026-09-04T15:10:00Z");
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar(nome, UUID.randomUUID() + "@test.local", "hash", agora));
        MembroFamilia membro = papel == PapelMembroFamilia.ADMINISTRADOR
                ? MembroFamilia.criarAdministrador(familia, usuario, agora)
                : MembroFamilia.criarMembro(familia, usuario, agora);
        return membroFamiliaRepository.saveAndFlush(membro);
    }

    private ItemCompra criarItemCompra(ContextoCompra contexto, String descricao, int ordem) {
        Instant agora = Instant.parse("2026-09-04T15:20:00Z");
        ItemLista itemLista = itemListaRepository.saveAndFlush(ItemLista.criar(
                contexto.lista(), descricao, BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, ordem,
                contexto.criador(), agora));
        return ItemCompra.criarDaPreparacao(contexto.compra(), itemLista);
    }

    private UUID id(Object entity) {
        return (UUID) campo(entity, "id");
    }

    private static Object campo(Object entity, String nome) {
        try {
            Field campo = entity.getClass().getDeclaredField(nome);
            campo.setAccessible(true);
            return campo.get(entity);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private record ContextoCompra(Familia familia, MembroFamilia criador, ListaCompra lista, Compra compra) {
    }
}
