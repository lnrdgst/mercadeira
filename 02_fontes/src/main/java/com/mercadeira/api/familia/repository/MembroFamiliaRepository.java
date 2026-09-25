package com.mercadeira.api.familia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
public interface MembroFamiliaRepository extends JpaRepository<MembroFamilia, UUID> {

    long deleteByFamilia_Id(UUID familiaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membro from MembroFamilia membro where membro.id = :id")
    Optional<MembroFamilia> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "familia")
    List<MembroFamilia> findByUsuario_IdAndStatusOrderByFamilia_NomeAsc(UUID usuarioId, StatusMembroFamilia status);

    Optional<MembroFamilia> findByFamilia_IdAndUsuario_Id(UUID familiaId, UUID usuarioId);

    @EntityGraph(attributePaths = "familia")
    Optional<MembroFamilia> findByFamilia_IdAndUsuario_IdAndStatus(
            UUID familiaId, UUID usuarioId, StatusMembroFamilia status);

    boolean existsByFamilia_IdAndUsuario_IdAndStatus(UUID familiaId, UUID usuarioId, StatusMembroFamilia status);

    @EntityGraph(attributePaths = "usuario")
    List<MembroFamilia> findByFamilia_IdAndStatusOrderByUsuario_NomeAscIdAsc(UUID familiaId, StatusMembroFamilia status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membro from MembroFamilia membro where membro.familia.id = :familiaId "
            + "and membro.status = :status and membro.papel = :papel")
    List<MembroFamilia> findByFamilia_IdAndStatusAndPapelForUpdate(
            @Param("familiaId") UUID familiaId, @Param("status") StatusMembroFamilia status,
            @Param("papel") PapelMembroFamilia papel);

    List<MembroFamilia> findByFamilia_IdAndStatusAndPapel(
            UUID familiaId, StatusMembroFamilia status, PapelMembroFamilia papel);
}
