package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.StatusCompra;
import org.springframework.stereotype.Service;

@Service
public class AlertaContinuidadeCompra {
    private final Clock clock;
    private final AlertaContinuidadeCompraProperties properties;

    public AlertaContinuidadeCompra(Clock clock, AlertaContinuidadeCompraProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public EstadoAlertaContinuidadeCompra avaliar(Compra compra, UUID participanteAtualId) {
        Instant agora = clock.instant();
        boolean necessario = compra.getStatus() == StatusCompra.EM_ANDAMENTO
                && participanteAtualId != null
                && participanteAtualId.equals(compra.getResponsavelOperacionalId())
                && !agora.isBefore(compra.getIniciadaEm().plus(properties.duracao()))
                && (compra.getAlertaContinuidadeAdiadoAte() == null
                        || !agora.isBefore(compra.getAlertaContinuidadeAdiadoAte()));
        return new EstadoAlertaContinuidadeCompra(necessario, compra.getIniciadaEm(), compra.getAlertaContinuidadeAdiadoAte());
    }

    public Instant proximoAlerta() {
        return clock.instant().plus(properties.duracao());
    }
}
