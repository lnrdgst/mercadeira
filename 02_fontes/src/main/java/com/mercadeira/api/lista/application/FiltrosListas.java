package com.mercadeira.api.lista.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

public record FiltrosListas(LocalDate criadaDe, LocalDate criadaAte, UUID criadaPorUsuarioId,
        UUID participanteMembroFamiliaId) {
    public FiltrosListas {
        if (criadaDe != null && criadaAte != null && criadaDe.isAfter(criadaAte))
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
    }
    public Instant inicio() { return criadaDe == null ? null : criadaDe.atStartOfDay(ZoneOffset.UTC).toInstant(); }
    public Instant fimExclusivo() { return criadaAte == null ? null : criadaAte.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(); }
}
