package com.mercadeira.api.compra.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.Test;

class FactoriesCompraTest {

    @Test
    void iniciaCompraComSnapshotsDaLista() {
        ListaCompra lista = newInstance(ListaCompra.class);
        MembroFamilia iniciador = membro("Ana", PapelMembroFamilia.ADMINISTRADOR);
        Instant iniciadaEm = Instant.parse("2026-09-04T15:30:00Z");
        set(lista, "nome", "Compras da semana");
        set(lista, "categoria", CategoriaCompra.SUPERMERCADO);
        set(lista, "estabelecimento", "Mercado Central");

        Compra compra = Compra.iniciar(lista, iniciador, iniciadaEm);

        assertThat(get(compra, "listaCompra")).isSameAs(lista);
        assertThat(get(compra, "status")).isEqualTo(StatusCompra.EM_ANDAMENTO);
        assertThat(get(compra, "iniciadaPorMembroFamilia")).isSameAs(iniciador);
        assertThat(get(compra, "iniciadaEm")).isEqualTo(iniciadaEm);
        assertThat(get(compra, "nomeListaSnapshot")).isEqualTo("Compras da semana");
        assertThat(get(compra, "categoriaSnapshot")).isEqualTo("SUPERMERCADO");
        assertThat(get(compra, "estabelecimentoSnapshot")).isEqualTo("Mercado Central");
    }

    @Test
    void criaParticipanteDaPreparacaoComSnapshotDoMembro() {
        Compra compra = newInstance(Compra.class);
        MembroFamilia membro = membro("Bia", PapelMembroFamilia.MEMBRO);
        ParticipanteLista origem = newInstance(ParticipanteLista.class);
        Instant geradoEm = Instant.parse("2026-09-04T15:31:00Z");
        set(origem, "membroFamilia", membro);

        ParticipanteCompra participante = ParticipanteCompra.criarDaPreparacao(compra, origem, geradoEm);

        assertThat(get(participante, "compra")).isSameAs(compra);
        assertThat(get(participante, "participanteListaOrigem")).isSameAs(origem);
        assertThat(get(participante, "membroFamilia")).isSameAs(membro);
        assertThat(get(participante, "nomeSnapshot")).isEqualTo("Bia");
        assertThat(get(participante, "papelSnapshot")).isEqualTo(PapelMembroFamilia.MEMBRO);
        assertThat(get(participante, "geradoEm")).isEqualTo(geradoEm);
    }

    @Test
    void criaParticipanteDiretoSemOrigemComSnapshotDoMembro() {
        Compra compra = newInstance(Compra.class);
        MembroFamilia membro = membro("Caio", PapelMembroFamilia.ADMINISTRADOR);
        Instant geradoEm = Instant.parse("2026-09-04T15:32:00Z");

        ParticipanteCompra participante = ParticipanteCompra.criarDireto(compra, membro, geradoEm);

        assertThat(get(participante, "compra")).isSameAs(compra);
        assertThat(get(participante, "participanteListaOrigem")).isNull();
        assertThat(get(participante, "membroFamilia")).isSameAs(membro);
        assertThat(get(participante, "nomeSnapshot")).isEqualTo("Caio");
        assertThat(get(participante, "papelSnapshot")).isEqualTo(PapelMembroFamilia.ADMINISTRADOR);
        assertThat(get(participante, "geradoEm")).isEqualTo(geradoEm);
    }

    @Test
    void criaItemDaPreparacaoComSnapshotsEPendente() {
        Compra compra = newInstance(Compra.class);
        ItemLista origem = newInstance(ItemLista.class);
        set(origem, "descricao", "Leite");
        set(origem, "quantidade", new BigDecimal("2.000"));
        set(origem, "unidadeMedida", UnidadeMedida.LITRO);
        set(origem, "marca", "Marca A");
        set(origem, "observacoes", "Integral");
        set(origem, "ordemExibicao", 3);

        ItemCompra item = ItemCompra.criarDaPreparacao(compra, origem);

        assertThat(get(item, "compra")).isSameAs(compra);
        assertThat(get(item, "itemListaOrigem")).isSameAs(origem);
        assertThat(get(item, "adicionadoDuranteCompra")).isEqualTo(false);
        assertThat(get(item, "status")).isEqualTo(StatusItemCompra.PENDENTE);
        assertThat(get(item, "descricaoSnapshot")).isEqualTo("Leite");
        assertThat(get(item, "quantidadeSnapshot")).isEqualTo(new BigDecimal("2.000"));
        assertThat(get(item, "unidadeMedidaSnapshot")).isEqualTo("LITRO");
        assertThat(get(item, "marcaSnapshot")).isEqualTo("Marca A");
        assertThat(get(item, "observacoesSnapshot")).isEqualTo("Integral");
        assertThat(get(item, "ordemExibicao")).isEqualTo(3);
    }

    private MembroFamilia membro(String nome, PapelMembroFamilia papel) {
        MembroFamilia membro = newInstance(MembroFamilia.class);
        Usuario usuario = newInstance(Usuario.class);
        set(usuario, "nome", nome);
        set(membro, "usuario", usuario);
        set(membro, "papel", papel);
        return membro;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Object get(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
