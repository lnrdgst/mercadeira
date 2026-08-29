package com.mercadeira.api.familia.repository;

import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.StatusFamilia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamiliaRepository extends JpaRepository<Familia, UUID> {

    Optional<Familia> findByCodigoIngressoAndStatus(String codigoIngresso, StatusFamilia status);
}
