package com.CitasHospital.Citas.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.CitasHospital.Citas.model.DisponibilidadMedico;

import jakarta.persistence.LockModeType;

/**
 * Proporciona acceso a los datos de las disponibilidades médicas.
 */
public interface DisponibilidadMedicoRepository extends JpaRepository<DisponibilidadMedico, Long> {
    List<DisponibilidadMedico> findByMedicoId(Long medicoId);

    @Query("""
            select count(d) > 0 from DisponibilidadMedico d
            where d.medico.id = :medicoId and d.fecha = :fecha
              and d.horaInicio < :fin and d.horaFin > :inicio
              and (:excluir is null or d.id <> :excluir)
            """)
    boolean existeCruce(@Param("medicoId") Long medicoId, @Param("fecha") LocalDate fecha,
            @Param("inicio") LocalTime inicio, @Param("fin") LocalTime fin, @Param("excluir") Long excluir);

    @Query("select d.medico.id from DisponibilidadMedico d where d.id = :id")
    Optional<Long> medicoId(@Param("id") Long id);

    List<DisponibilidadMedico> findByMedicoIdAndFecha(
            Long medicoId,
            LocalDate fecha
    );

    boolean existsByMedicoIdAndFechaAndHoraInicioAndHoraFin(
            Long medicoId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin
    );

    boolean existsByMedicoIdAndFechaAndHoraInicioAndHoraFinAndIdNot(
            Long medicoId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            Long id
    );

    /**
     * Obtiene la disponibilidad aplicando un bloqueo pesimista durante la transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DisponibilidadMedico d WHERE d.id = :id")
    Optional<DisponibilidadMedico> buscarPorIdConBloqueo(@Param("id") Long id);
}
