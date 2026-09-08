package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
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
class ConsultarCompraDaListaApplicationTests {
    @Container @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @DynamicPropertySource static void jwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }
    @Autowired private ConsultarCompraDaLista consultarCompra;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroRepository;
    @Autowired private ListaCompraRepository listaRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;

    @Test
    void participanteConsultaCompraComSnapshotsEOrdenacao() {
        Contexto contexto = contexto();
        ItemLista segundo = itemListaRepository.saveAndFlush(ItemLista.criar(contexto.lista, "Feijao", BigDecimal.TWO,
                UnidadeMedida.UNIDADE, "Marca B", "Obs B", 2, contexto.criador, agora()));
        iniciarCompra.iniciar(contexto.usuario.getId(), contexto.familia.getId(), contexto.lista.getId());

        ResultadoConsultaCompra resultado = consultarCompra.consultar(contexto.usuario.getId(), contexto.familia.getId(), contexto.lista.getId());

        assertThat(resultado.compra().getId()).isNotNull();
        assertThat(resultado.participanteCompra()).isTrue();
        assertThat(resultado.participantes()).extracting(p -> p.getMembroFamilia().getId()).containsExactly(contexto.criador.getId());
        assertThat(resultado.participantes().getFirst().getNomeSnapshot()).isEqualTo("Ana");
        assertThat(resultado.participantes().getFirst().getPapelSnapshot()).isEqualTo(PapelMembroFamilia.ADMINISTRADOR);
        assertThat(resultado.itens()).extracting(item -> item.getOrdemExibicao()).containsExactly(1, 2);
        assertThat(resultado.itens()).allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(StatusItemCompra.PENDENTE));
        assertThat(resultado.itens().get(1).getDescricaoSnapshot()).isEqualTo(segundo.getDescricao());
        assertThat(resultado.itens().getFirst().getItemListaOrigem().getId()).isNotNull();
    }

    @Test
    void membroAtivoObservadorConsultaSemParticiparDaCompra() {
        Contexto contexto = contexto();
        MembroFamilia observador = membro(contexto.familia, "Bia", PapelMembroFamilia.MEMBRO);
        iniciarCompra.iniciar(contexto.usuario.getId(), contexto.familia.getId(), contexto.lista.getId());

        ResultadoConsultaCompra resultado = consultarCompra.consultar(
                observador.getUsuario().getId(), contexto.familia.getId(), contexto.lista.getId());

        assertThat(resultado.participanteCompra()).isFalse();
        assertThat(resultado.compra().getId()).isNotNull();
    }

    @Test
    void rejeitaContextoInvalidoEAusenciaDeCompra() {
        Contexto contexto = contexto();
        Contexto outra = contexto();
        assertThatThrownBy(() -> consultarCompra.consultar(contexto.usuario.getId(), outra.familia.getId(), contexto.lista.getId()))
                .isInstanceOf(ListaCompraNaoEncontradaException.class);
        Usuario externo = usuarioRepository.saveAndFlush(Usuario.criar("Externo", UUID.randomUUID() + "@test.local", "hash", agora()));
        assertThatThrownBy(() -> consultarCompra.consultar(externo.getId(), contexto.familia.getId(), contexto.lista.getId()))
                .isInstanceOf(MembroFamiliaInvalidoException.class);
        assertThatThrownBy(() -> consultarCompra.consultar(contexto.usuario.getId(), contexto.familia.getId(), contexto.lista.getId()))
                .isInstanceOf(CompraNaoEncontradaException.class);
    }

    @Test
    void consultasRepetidasNaoAlteramEstado() {
        Contexto contexto = contexto();
        Compra compra = iniciarCompra.iniciar(contexto.usuario.getId(), contexto.familia.getId(), contexto.lista.getId()).compra();
        ResultadoConsultaCompra primeira = consultarCompra.consultar(contexto.usuario.getId(), contexto.familia.getId(), contexto.lista.getId());
        ResultadoConsultaCompra segunda = consultarCompra.consultar(contexto.usuario.getId(), contexto.familia.getId(), contexto.lista.getId());
        assertThat(primeira.compra().getId()).isEqualTo(compra.getId());
        assertThat(segunda.compra().getId()).isEqualTo(compra.getId());
        assertThat(primeira.itens()).hasSameSizeAs(segunda.itens());
        assertThat(contexto.lista.getStatus().name()).isEqualTo("EM_COMPRA");
    }

    private Contexto contexto() {
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar("Ana", UUID.randomUUID() + "@test.local", "hash", agora()));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar("Familia", UUID.randomUUID().toString().replace("-", ""), usuario, agora()));
        MembroFamilia criador = membroRepository.saveAndFlush(MembroFamilia.criarAdministrador(familia, usuario, agora()));
        ListaCompra lista = listaRepository.saveAndFlush(ListaCompra.criar(familia, "Lista", CategoriaCompra.SUPERMERCADO, "Mercado", criador, agora()));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, criador, agora()));
        itemListaRepository.saveAndFlush(ItemLista.criar(lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, "Marca A", "Obs A", 1, criador, agora()));
        return new Contexto(usuario, familia, criador, lista);
    }

    private MembroFamilia membro(Familia familia, String nome, PapelMembroFamilia papel) {
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar(nome, UUID.randomUUID() + "@test.local", "hash", agora()));
        return membroRepository.saveAndFlush(papel == PapelMembroFamilia.ADMINISTRADOR
                ? MembroFamilia.criarAdministrador(familia, usuario, agora()) : MembroFamilia.criarMembro(familia, usuario, agora()));
    }
    private Instant agora() { return Instant.parse("2026-09-04T20:00:00Z"); }
    private record Contexto(Usuario usuario, Familia familia, MembroFamilia criador, ListaCompra lista) { }
}
