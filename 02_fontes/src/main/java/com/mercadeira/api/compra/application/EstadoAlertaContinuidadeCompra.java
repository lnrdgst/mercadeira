package com.mercadeira.api.compra.application;

import java.time.Instant;

public record EstadoAlertaContinuidadeCompra(boolean necessario, Instant iniciadaEm, Instant adiadoAte) {
}
