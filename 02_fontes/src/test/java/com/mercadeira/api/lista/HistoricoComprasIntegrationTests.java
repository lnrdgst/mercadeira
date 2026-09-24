package com.mercadeira.api.lista;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.mercadeira.api.compra.application.IniciarCompra;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.lista.application.AdicionarItemLista;
import com.mercadeira.api.lista.application.CriarListaCompra;
import com.mercadeira.api.lista.application.ListarListasFamilia;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class HistoricoComprasIntegrationTests {
    private static final Instant AGORA = Instant.parse("2026-09-23T12:00:00Z");
    @Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @Autowired CadastrarUsuario usuarios; @Autowired CriarFamilia familias; @Autowired CriarListaCompra listas;
    @Autowired AdicionarItemLista itens; @Autowired IniciarCompra iniciar; @Autowired ListarListasFamilia historico; @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;

    @BeforeEach void tempo() { given(clock.instant()).willReturn(AGORA); given(clock.getZone()).willReturn(ZoneOffset.UTC); }

    @Test void classificaHistoricoOrdenaPaginaIsolaFamiliasELimitaTamanho() {
        var ana = usuario("Ana"); var familia = familias.criar(ana.getId(), "Casa Ana");
        var antigaA = finalizada(ana, familia.getId(), "Antiga A", AGORA.minusSeconds(15 * 86400));
        var antigaB = finalizada(ana, familia.getId(), "Antiga B", AGORA.minusSeconds(20 * 86400));
        finalizada(ana, familia.getId(), "Exata", AGORA.minusSeconds(14 * 86400));
        finalizada(ana, familia.getId(), "Recente", AGORA.minusSeconds(13 * 86400));
        var preparacao = listas.criar(ana.getId(), familia.getId(), "Preparacao", CategoriaCompra.OUTROS, null);
        var andamento = iniciar(ana, familia.getId(), "Andamento");
        var bia = usuario("Bia"); var outra = familias.criar(bia.getId(), "Casa Bia"); finalizada(bia, outra.getId(), "Outra", AGORA.minusSeconds(30 * 86400));

        var primeira = historico.historico(ana.getId(), familia.getId(), 0, 1);
        assertThat(primeira.getContent()).extracting(c -> c.getListaCompra().getNome()).containsExactly("Antiga A");
        assertThat(primeira.getSize()).isEqualTo(1); assertThat(primeira.getTotalElements()).isEqualTo(2); assertThat(primeira.getTotalPages()).isEqualTo(2); assertThat(primeira.hasNext()).isTrue();
        var segunda = historico.historico(ana.getId(), familia.getId(), 1, 1);
        assertThat(segunda.getContent()).extracting(c -> c.getListaCompra().getNome()).containsExactly("Antiga B"); assertThat(segunda.hasNext()).isFalse();
        assertThat(historico.historico(ana.getId(), familia.getId(), 0, 999).getSize()).isEqualTo(50);
        assertThat(historico.totalHistorico(ana.getId(), familia.getId())).isEqualTo(2);
        assertThat(historico.listar(ana.getId(), familia.getId())).extracting(l -> l.getNome()).contains("Exata", "Recente", "Preparacao", "Andamento").doesNotContain("Antiga A", "Antiga B", "Outra");
        assertThatThrownBy(() -> historico.historico(ana.getId(), outra.getId(), 0, 20)).isInstanceOf(RuntimeException.class);
        assertThat(preparacao.getId()).isNotNull(); assertThat(andamento.getId()).isNotNull();
    }

    private com.mercadeira.api.lista.domain.ListaCompra iniciar(Usuario usuario, UUID familia, String nome) {
        var lista = listas.criar(usuario.getId(), familia, nome, CategoriaCompra.OUTROS, null);
        itens.adicionar(usuario.getId(), familia, lista.getId(), "Item", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null);
        iniciar.iniciar(usuario.getId(), familia, lista.getId()); return lista;
    }
    private com.mercadeira.api.lista.domain.ListaCompra finalizada(Usuario usuario, UUID familia, String nome, Instant fim) {
        var lista = iniciar(usuario, familia, nome);
        var timestamp = java.sql.Timestamp.from(fim);
        jdbc.update("update compra set status='FINALIZADA', finalizada_em=?, finalizada_por_participante_compra_id=(select id from participante_compra where compra_id=compra.id limit 1) where lista_compra_id=?", timestamp, lista.getId());
        jdbc.update("update lista_compra set status='FINALIZADA', atualizada_em=? where id=?", timestamp, lista.getId()); return lista;
    }
    private Usuario usuario(String nome) { return usuarios.cadastrar(nome, UUID.randomUUID()+"@test.local", "senha-original"); }
}
