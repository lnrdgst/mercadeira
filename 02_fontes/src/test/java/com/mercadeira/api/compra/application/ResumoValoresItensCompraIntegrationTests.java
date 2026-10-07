package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.api.CompraResponse;
import com.mercadeira.api.compra.domain.ItemCompra;
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
class ResumoValoresItensCompraIntegrationTests {
    @Container @ServiceConnection static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @DynamicPropertySource static void jwt(DynamicPropertyRegistry registry) { registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM="); }

    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private ColocarItemNoCarrinho colocarNoCarrinho;
    @Autowired private RegistrarDadosItemCompra registrarDados;
    @Autowired private ConsultarCompraDaLista consultarCompra;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private FamiliaRepository familias;
    @Autowired private MembroFamiliaRepository membros;
    @Autowired private ListaCompraRepository listas;
    @Autowired private ParticipanteListaRepository participantesLista;
    @Autowired private ItemListaRepository itensLista;
    @Autowired private CompraRepository compras;
    @Autowired private ItemCompraRepository itensCompra;

    @Test
    void totalConsideraSomenteItensNoCarrinhoComPreco() {
        Contexto contexto = criarContexto();
        var itens = itensCompra.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(contexto.compraId());
        for (ItemCompra item : itens) colocarNoCarrinho.executar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), item.getId());
        registrarDados.executar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), itens.get(0).getId(), new BigDecimal("27.00"), new BigDecimal("2"));

        CompraResponse semPreco = CompraResponse.from(consultarCompra.consultar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId()), contexto.usuario().getId());
        assertThat(semPreco.totalItensComprados()).isEqualByComparingTo("54.00");
        assertThat(semPreco.quantidadeItensNoCarrinhoSemPreco()).isEqualTo(1);

        registrarDados.executar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId(), itens.get(1).getId(), new BigDecimal("0.89"), new BigDecimal("10"));
        CompraResponse completo = CompraResponse.from(consultarCompra.consultar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId()), contexto.usuario().getId());
        assertThat(completo.totalItensComprados()).isEqualByComparingTo("62.90");
        assertThat(completo.quantidadeItensNoCarrinhoSemPreco()).isZero();

        itens.get(0).solicitarRemocao(contexto.membro(), agora());
        itens.get(0).aprovarRemocao(contexto.membro(), agora());
        CompraResponse removido = CompraResponse.from(consultarCompra.consultar(contexto.usuario().getId(), contexto.familia().getId(), contexto.lista().getId()), contexto.usuario().getId());
        assertThat(removido.totalItensComprados()).isEqualByComparingTo("8.90");
    }

    private Contexto criarContexto() {
        Usuario usuario = usuarios.saveAndFlush(Usuario.criar("Ana", "ana-" + UUID.randomUUID() + "@test.local", "hash", agora()));
        Familia familia = familias.saveAndFlush(Familia.criar("Familia", UUID.randomUUID().toString().replace("-", ""), usuario, agora()));
        MembroFamilia membro = membros.saveAndFlush(MembroFamilia.criarAdministrador(familia, usuario, agora()));
        ListaCompra lista = listas.saveAndFlush(ListaCompra.criar(familia, "Lista", CategoriaCompra.SUPERMERCADO, null, membro, agora()));
        participantesLista.saveAndFlush(ParticipanteLista.criar(lista, membro, agora()));
        itensLista.saveAndFlush(ItemLista.criar(lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, membro, agora()));
        itensLista.saveAndFlush(ItemLista.criar(lista, "Suco", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 2, membro, agora()));
        iniciarCompra.iniciar(usuario.getId(), familia.getId(), lista.getId());
        return new Contexto(usuario, familia, membro, lista, compras.findByListaCompra_Id(lista.getId()).orElseThrow().getId());
    }
    private static Instant agora() { return Instant.parse("2026-10-06T12:00:00Z"); }
    private record Contexto(Usuario usuario, Familia familia, MembroFamilia membro, ListaCompra lista, UUID compraId) { }
}
