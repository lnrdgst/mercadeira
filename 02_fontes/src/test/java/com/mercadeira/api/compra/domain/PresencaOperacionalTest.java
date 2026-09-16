package com.mercadeira.api.compra.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import com.mercadeira.api.familia.domain.*;
import com.mercadeira.api.lista.domain.*;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PresencaOperacionalTest {
    private final Instant t = Instant.parse("2026-09-15T12:00:00Z");

    private ParticipanteCompra participante() {
        var usuario = Usuario.criar("Ana", "ana@test.local", "hash", t);
        var familia = Familia.criar("Casa", "codigo", usuario, t);
        var membro = MembroFamilia.criarAdministrador(familia, usuario, t);
        var lista = ListaCompra.criar(familia, "Lista", CategoriaCompra.OUTROS, null, membro, t);
        var compra = Compra.iniciar(lista, membro, t);
        var participante = ParticipanteCompra.criarDireto(compra, membro, t);
        ReflectionTestUtils.setField(participante, "id", UUID.randomUUID());
        return participante;
    }

    @Test void declaraSaiRetornaSemAlterarSnapshotsOuTimestampNoReplay() {
        var p = participante();
        var id = p.getId();
        assertThat(p.getPresencaOperacional()).isEqualTo(PresencaOperacional.NAO_INFORMADA);
        assertThat(p.getPresencaAlteradaEm()).isNull();
        assertThat(p.estaPresente()).isFalse();
        assertThat(p.alterarPresenca(PresencaOperacional.PRESENTE, t.plusNanos(123456789))).isTrue();
        var declarado = p.getPresencaAlteradaEm();
        assertThat(declarado).isEqualTo(t.plusNanos(123456000));
        assertThat(p.alterarPresenca(PresencaOperacional.PRESENTE, t.plusSeconds(1))).isFalse();
        assertThat(p.getPresencaAlteradaEm()).isEqualTo(declarado);
        p.alterarPresenca(PresencaOperacional.NAO_PRESENTE, t.plusSeconds(2));
        assertThat(p.estaPresente()).isFalse();
        p.alterarPresenca(PresencaOperacional.PRESENTE, t.plusSeconds(3));
        assertThat(p.estaPresente()).isTrue();
        assertThat(p.getPresencaAlteradaEm()).isEqualTo(t.plusSeconds(3));
        assertThat(p.getId()).isEqualTo(id);
        assertThat(p.getNomeSnapshot()).isEqualTo("Ana");
        assertThat(p.getPapelSnapshot()).isEqualTo(PapelMembroFamilia.ADMINISTRADOR);
        assertThat(p.getGeradoEm()).isEqualTo(t);
    }

    @Test void primeiraDeclaracaoPodeSerNaoPresenteEEntradaInvalidaNaoMudaEstado() {
        var p = participante();
        assertThatThrownBy(() -> p.alterarPresenca(null, t)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.alterarPresenca(PresencaOperacional.PRESENTE, null)).isInstanceOf(IllegalArgumentException.class);
        p.alterarPresenca(PresencaOperacional.NAO_PRESENTE, t);
        assertThatThrownBy(() -> p.alterarPresenca(PresencaOperacional.NAO_INFORMADA, t)).isInstanceOf(IllegalArgumentException.class);
        assertThat(p.getPresencaOperacional()).isEqualTo(PresencaOperacional.NAO_PRESENTE);
        assertThat(p.getPresencaAlteradaEm()).isEqualTo(t);
    }

    @Test void encerramentoBloqueiaInclusiveRepeticao() {
        var p = participante();
        p.alterarPresenca(PresencaOperacional.PRESENTE, t);
        for (var estado : new StatusCompra[]{StatusCompra.FINALIZADA, StatusCompra.CANCELADA}) {
            ReflectionTestUtils.setField(p.getCompra(), "status", estado);
            assertThatThrownBy(() -> p.alterarPresenca(PresencaOperacional.PRESENTE, t.plusSeconds(1)))
                    .isInstanceOf(com.mercadeira.api.compra.application.CompraForaDeAndamentoException.class);
            assertThat(p.getPresencaAlteradaEm()).isEqualTo(t);
        }
    }
}