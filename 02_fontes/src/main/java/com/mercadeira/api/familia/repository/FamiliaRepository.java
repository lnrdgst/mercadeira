package com.mercadeira.api.familia.repository;

import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.StatusFamilia;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;

public interface FamiliaRepository extends JpaRepository<Familia, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select familia from Familia familia where familia.id = :id")
    Optional<Familia> findByIdForUpdate(@Param("id") UUID id);

    Optional<Familia> findByCodigoIngressoAndStatus(String codigoIngresso, StatusFamilia status);

    boolean existsByCodigoIngresso(String codigoIngresso);
}
