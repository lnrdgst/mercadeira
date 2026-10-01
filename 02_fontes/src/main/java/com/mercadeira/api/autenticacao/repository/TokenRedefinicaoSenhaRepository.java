package com.mercadeira.api.autenticacao.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.autenticacao.domain.TokenRedefinicaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TokenRedefinicaoSenhaRepository extends JpaRepository<TokenRedefinicaoSenha, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenRedefinicaoSenha> findByTokenHash(String tokenHash);

    Optional<TokenRedefinicaoSenha> findTopByUsuarioIdAndUsadoEmIsNullOrderByCriadoEmDesc(UUID usuarioId);

    @Modifying
    @Query("update TokenRedefinicaoSenha t set t.usadoEm = :agora where t.usuario.id = :usuarioId and t.usadoEm is null")
    int invalidarAtivosDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);
}
