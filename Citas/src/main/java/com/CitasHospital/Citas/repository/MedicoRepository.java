package com.CitasHospital.Citas.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;

import com.CitasHospital.Citas.model.Medico;

/**
 * Proporciona acceso a los datos de los médicos.
 */
public interface MedicoRepository extends JpaRepository<Medico, Long> {
    List<Medico> findByActivoTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Medico m where m.id = :id")
    Optional<Medico> buscarPorIdConBloqueo(@Param("id") Long id);
}
