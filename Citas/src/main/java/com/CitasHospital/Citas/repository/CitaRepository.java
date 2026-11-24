package com.CitasHospital.Citas.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.CitasHospital.Citas.model.Cita;
import com.CitasHospital.Citas.model.EstadoCita;

import jakarta.persistence.LockModeType;

/**
 * Proporciona acceso a los datos de las citas.
 */
public interface CitaRepository extends JpaRepository<Cita, Long> {
    List<Cita> findByUsuarioIdOrderByFechaDescHoraInicioDesc(Long usuarioId);

    List<Cita> findAllByOrderByFechaDescHoraInicioDesc();

    List<Cita> findByUsuarioIdAndEstadoIn(
            Long usuarioId,
            Collection<EstadoCita> estados
    );

    List<Cita> findByDisponibilidadIdAndEstadoIn(
            Long disponibilidadId,
            Collection<EstadoCita> estados
    );

    boolean existsByDisponibilidadIdAndEstadoIn(
            Long disponibilidadId,
            Collection<EstadoCita> estados
    );

    boolean existsByDisponibilidadId(Long disponibilidadId);

    /**
     * Indica si existe una cita activa que se solapa con el horario seleccionado.
     */
    @Query("""
            select count(c) > 0 from Cita c
            where c.medico.id = :medicoId and c.fecha = :fecha
              and c.estado in :estados and c.horaInicio < :horaFin and c.horaFin > :horaInicio
            """)
    boolean existeCitaActivaMedicoEnHorario(@Param("medicoId") Long medicoId,
            @Param("fecha") java.time.LocalDate fecha,
            @Param("horaInicio") java.time.LocalTime horaInicio,
            @Param("horaFin") java.time.LocalTime horaFin,
            @Param("estados") Collection<EstadoCita> estados);

    List<Cita> findByMedicoIdAndFechaAndEstadoIn(Long medicoId, java.time.LocalDate fecha,
            Collection<EstadoCita> estados);

    interface Referencias {
        Long getUsuarioId();
        Long getMedicoId();
        Long getDisponibilidadId();
    }
    @Query("select c.usuario.id as usuarioId, c.medico.id as medicoId, c.disponibilidad.id as disponibilidadId from Cita c where c.id = :id")
    Optional<Referencias> referencias(@Param("id") Long id);

    /**
     * Indica si el usuario tiene una cita activa que se solapa con el horario seleccionado.
     */
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Cita c
            WHERE c.usuario.id = :usuarioId
              AND c.estado IN :estados
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
            """)
    boolean existeCitaActivaUsuarioEnHorario(
            @Param("usuarioId") Long usuarioId,
            @Param("fecha") java.time.LocalDate fecha,
            @Param("horaInicio") java.time.LocalTime horaInicio,
            @Param("horaFin") java.time.LocalTime horaFin,
            @Param("estados") Collection<EstadoCita> estados
    );

    /**
     * Obtiene la cita aplicando un bloqueo pesimista durante la transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cita c WHERE c.id = :id")
    Optional<Cita> buscarPorIdConBloqueo(@Param("id") Long id);
}
