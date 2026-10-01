package com.mercadeira.api.usuario.api;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public record AlterarSenhaRequest(@NotBlank @Size(max=255) String senhaAtual, @NotBlank @Size(max=255) String novaSenha) {}
