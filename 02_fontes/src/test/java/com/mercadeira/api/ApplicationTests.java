package com.mercadeira.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.familia.domain.StatusFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
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

    private static final Instant BASE_TIME = Instant.parse("2026-08-29T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private FamiliaRepository familiaRepository;

    @Autowired
    private MembroFamiliaRepository membroFamiliaRepository;

    @Autowired
    private SolicitacaoEntradaFamiliaRepository solicitacaoEntradaFamiliaRepository;

    @Autowired
    private ParticipanteListaRepository participanteListaRepository;

    @Autowired
    private ItemListaRepository itemListaRepository;

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private ItemCompraRepository itemCompraRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void buscaFamiliaAtivaPorCodigoIngresso() {
        UUID criadorId = inserirUsuario();
        UUID familiaAtivaId = inserirFamilia(criadorId, StatusFamilia.ATIVA, "CODIGO-ATIVO");
        inserirFamilia(criadorId, StatusFamilia.INATIVA, "CODIGO-INATIVO");

        assertThat(familiaRepository.findByCodigoIngressoAndStatus("CODIGO-ATIVO", StatusFamilia.ATIVA))
                .map(this::idDe)
                .contains(familiaAtivaId);
        assertThat(familiaRepository.findByCodigoIngressoAndStatus("CODIGO-INATIVO", StatusFamilia.ATIVA))
                .isEmpty();
    }

    @Test
    void localizaUnicoVinculoAtivoDoUsuario() {
        UUID usuarioId = inserirUsuario();
        UUID familiaAtivaId = inserirFamilia(usuarioId, StatusFamilia.ATIVA, "FAMILIA-ATIVA");
        UUID familiaInativaId = inserirFamilia(usuarioId, StatusFamilia.ATIVA, "FAMILIA-INATIVA");
        UUID membroAtivoId = inserirMembro(familiaAtivaId, usuarioId, StatusMembroFamilia.ATIVO);
        inserirMembro(familiaInativaId, usuarioId, StatusMembroFamilia.INATIVO);

        assertThat(membroFamiliaRepository.findByUsuario_IdAndStatus(usuarioId, StatusMembroFamilia.ATIVO))
                .map(this::idDe)
                .contains(membroAtivoId);
        assertThat(membroFamiliaRepository.existsByUsuario_IdAndStatus(usuarioId, StatusMembroFamilia.ATIVO))
                .isTrue();
        assertThat(membroFamiliaRepository.findByFamilia_IdAndUsuario_Id(familiaAtivaId, usuarioId))
                .isPresent();
    }

    @Test
    void localizaSolicitacaoPendentePorFamiliaESolicitante() {
        UUID criadorId = inserirUsuario();
        UUID solicitanteId = inserirUsuario();
        UUID familiaId = inserirFamilia(criadorId, StatusFamilia.ATIVA, "FAMILIA-SOLICITACAO");
        UUID solicitacaoId = inserirSolicitacao(familiaId, solicitanteId);

        assertThat(solicitacaoEntradaFamiliaRepository
                .findByFamilia_IdAndStatusOrderBySolicitadaEmAsc(familiaId, StatusSolicitacaoEntradaFamilia.PENDENTE))
                .extracting(this::idDe)
                .containsExactly(solicitacaoId);
        assertThat(solicitacaoEntradaFamiliaRepository
                .findByFamilia_IdAndSolicitanteUsuario_IdAndStatus(
                        familiaId, solicitanteId, StatusSolicitacaoEntradaFamilia.PENDENTE))
                .map(this::idDe)
                .contains(solicitacaoId);
    }

    @Test
    void listaSomenteParticipantesAtivos() {
        UUID usuarioId = inserirUsuario();
        UUID segundoUsuarioId = inserirUsuario();
        UUID familiaId = inserirFamilia(usuarioId, StatusFamilia.ATIVA, "FAMILIA-PARTICIPANTE");
        UUID membroAtivoId = inserirMembro(familiaId, usuarioId, StatusMembroFamilia.ATIVO);
        UUID membroSaiuId = inserirMembro(familiaId, segundoUsuarioId, StatusMembroFamilia.ATIVO);
        UUID listaId = inserirLista(familiaId, membroAtivoId);
        UUID participanteAtivoId = inserirParticipante(listaId, membroAtivoId, null);
        inserirParticipante(listaId, membroSaiuId, BASE_TIME.plusSeconds(60));

        assertThat(participanteListaRepository.findByListaCompra_IdAndSaiuEmIsNull(listaId))
                .extracting(this::idDe)
                .containsExactly(participanteAtivoId);
        assertThat(participanteListaRepository.findByListaCompra_IdAndMembroFamilia_Id(listaId, membroAtivoId))
                .isPresent();
    }

    @Test
    void listaItensAtivosNaOrdemDeExibicao() {
        UUID usuarioId = inserirUsuario();
        UUID familiaId = inserirFamilia(usuarioId, StatusFamilia.ATIVA, "FAMILIA-ITENS");
        UUID membroId = inserirMembro(familiaId, usuarioId, StatusMembroFamilia.ATIVO);
        UUID listaId = inserirLista(familiaId, membroId);
        UUID segundoId = inserirItemLista(listaId, membroId, 2, null);
        UUID primeiroId = inserirItemLista(listaId, membroId, 1, null);
        inserirItemLista(listaId, membroId, 0, BASE_TIME.plusSeconds(60));

        assertThat(itemListaRepository.findByListaCompra_IdAndRemovidoEmIsNullOrderByOrdemExibicaoAscIdAsc(listaId))
                .extracting(this::idDe)
                .containsExactly(primeiroId, segundoId);
    }

    @Test
    void localizaCompraPorListaEHistoricoFinalizadoPorFamilia() {
        UUID usuarioId = inserirUsuario();
        UUID familiaId = inserirFamilia(usuarioId, StatusFamilia.ATIVA, "FAMILIA-COMPRA");
        UUID membroId = inserirMembro(familiaId, usuarioId, StatusMembroFamilia.ATIVO);
        UUID primeiraListaId = inserirLista(familiaId, membroId);
        UUID segundaListaId = inserirLista(familiaId, membroId);
        UUID primeiraCompraId = inserirCompra(primeiraListaId, membroId, StatusCompra.FINALIZADA, BASE_TIME.plusSeconds(60));
        UUID segundaCompraId = inserirCompra(segundaListaId, membroId, StatusCompra.FINALIZADA, BASE_TIME.plusSeconds(120));

        assertThat(compraRepository.findByListaCompra_Id(primeiraListaId)).map(this::idDe).contains(primeiraCompraId);
        assertThat(compraRepository.findByListaCompra_Familia_IdAndStatusOrderByFinalizadaEmDesc(
                familiaId, StatusCompra.FINALIZADA))
                .extracting(this::idDe)
                .containsExactly(segundaCompraId, primeiraCompraId);
    }

    @Test
    void listaItensDaCompraNaOrdemEFiltraPorStatus() {
        UUID usuarioId = inserirUsuario();
        UUID familiaId = inserirFamilia(usuarioId, StatusFamilia.ATIVA, "FAMILIA-ITEM-COMPRA");
        UUID membroId = inserirMembro(familiaId, usuarioId, StatusMembroFamilia.ATIVO);
        UUID listaId = inserirLista(familiaId, membroId);
        UUID compraId = inserirCompra(listaId, membroId, StatusCompra.EM_ANDAMENTO, null);
        UUID segundoId = inserirItemCompra(compraId, 2, StatusItemCompra.PENDENTE);
        UUID primeiroId = inserirItemCompra(compraId, 1, StatusItemCompra.PENDENTE);
        UUID terceiroId = inserirItemCompra(compraId, 3, StatusItemCompra.NO_CARRINHO);

        assertThat(itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compraId))
                .extracting(this::idDe)
                .containsExactly(primeiroId, segundoId, terceiroId);
        assertThat(itemCompraRepository.findByCompra_IdAndStatusOrderByOrdemExibicaoAscIdAsc(
                compraId, StatusItemCompra.PENDENTE))
                .extracting(this::idDe)
                .containsExactly(primeiroId, segundoId);
    }

    private UUID inserirUsuario() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO usuario (id, nome, email, criado_em, atualizado_em) VALUES (?, ?, ?, ?, ?)",
                id, "Usuario " + id, id + "@test.local", timestamp(BASE_TIME), timestamp(BASE_TIME));
        return id;
    }

    private UUID inserirFamilia(UUID criadaPorUsuarioId, StatusFamilia status, String codigoIngresso) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO familia (id, nome, codigo_ingresso, status, criada_por_usuario_id, criada_em, atualizada_em) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, "Familia " + id, codigoIngresso, status.name(), criadaPorUsuarioId,
                timestamp(BASE_TIME), timestamp(BASE_TIME));
        return id;
    }

    private UUID inserirMembro(UUID familiaId, UUID usuarioId, StatusMembroFamilia status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) "
                        + "VALUES (?, ?, ?, 'MEMBRO', ?, ?, ?)",
                id, familiaId, usuarioId, status.name(), timestamp(BASE_TIME), timestamp(BASE_TIME));
        return id;
    }

    private UUID inserirSolicitacao(UUID familiaId, UUID solicitanteUsuarioId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO solicitacao_entrada_familia "
                        + "(id, familia_id, solicitante_usuario_id, status, solicitada_em) VALUES (?, ?, ?, 'PENDENTE', ?)",
                id, familiaId, solicitanteUsuarioId, timestamp(BASE_TIME));
        return id;
    }

    private UUID inserirLista(UUID familiaId, UUID criadaPorMembroFamiliaId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lista_compra "
                        + "(id, familia_id, nome, categoria, status, criada_por_membro_familia_id, criada_em, atualizada_em) "
                        + "VALUES (?, ?, ?, ?, 'EM_PREPARACAO', ?, ?, ?)",
                id, familiaId, "Lista " + id, "Mercado", criadaPorMembroFamiliaId,
                timestamp(BASE_TIME), timestamp(BASE_TIME));
        return id;
    }

    private UUID inserirParticipante(UUID listaId, UUID membroId, Instant saiuEm) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO participante_lista (id, lista_compra_id, membro_familia_id, entrou_em, saiu_em) "
                        + "VALUES (?, ?, ?, ?, ?)",
                id, listaId, membroId, timestamp(BASE_TIME), saiuEm == null ? null : timestamp(saiuEm));
        return id;
    }

    private UUID inserirItemLista(UUID listaId, UUID membroId, int ordemExibicao, Instant removidoEm) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO item_lista "
                        + "(id, lista_compra_id, descricao, ordem_exibicao, removido_em, adicionado_por_membro_familia_id, criado_em, atualizado_em) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, listaId, "Item " + id, ordemExibicao, removidoEm == null ? null : timestamp(removidoEm),
                membroId, timestamp(BASE_TIME), timestamp(BASE_TIME));
        return id;
    }

    private UUID inserirCompra(UUID listaId, UUID membroId, StatusCompra status, Instant finalizadaEm) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compra "
                        + "(id, lista_compra_id, iniciada_por_membro_familia_id, nome_lista_snapshot, categoria_snapshot, status, iniciada_em, finalizada_em) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, listaId, membroId, "Lista snapshot", "Mercado", status.name(), timestamp(BASE_TIME),
                finalizadaEm == null ? null : timestamp(finalizadaEm));
        return id;
    }

    private UUID inserirItemCompra(UUID compraId, int ordemExibicao, StatusItemCompra status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO item_compra "
                        + "(id, compra_id, adicionado_durante_compra, ordem_exibicao, descricao_snapshot, status) "
                        + "VALUES (?, ?, TRUE, ?, ?, ?)",
                id, compraId, ordemExibicao, "Item snapshot " + id, status.name());
        return id;
    }

    private UUID idDe(Object entity) {
        return (UUID) entityManagerFactory.getPersistenceUnitUtil().getIdentifier(entity);
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
