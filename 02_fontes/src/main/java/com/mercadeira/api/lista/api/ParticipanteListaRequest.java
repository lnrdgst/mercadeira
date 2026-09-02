package com.mercadeira.api.lista.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ParticipanteListaRequest(@NotNull UUID membroFamiliaId) { }
