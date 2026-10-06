package com.mercadeira.api.autenticacao.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.autenticacao.domain.SessaoPersistente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessaoPersistenteRepository extends JpaRepository<SessaoPersistente, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SessaoPersistente> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update SessaoPersistente s set s.revogadoEm = :agora where s.usuario.id = :usuarioId and s.revogadoEm is null")
    int revogarAtivasDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);
}
