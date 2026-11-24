package com.CitasHospital.Citas.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.CitasHospital.Citas.model.DisponibilidadMedico;
import com.CitasHospital.Citas.model.Medico;
import com.CitasHospital.Citas.repository.CitaRepository;
import com.CitasHospital.Citas.repository.DisponibilidadMedicoRepository;

/**
 * Contiene la lógica de negocio relacionada con la gestión de disponibilidades médicas.
 */
@Service
public class DisponibilidadMedicoService {
    private final DisponibilidadMedicoRepository disponibilidadMedicoRepository;
    private final BloqueosAgenda bloqueos;
    private final TiempoAgenda tiempo;
    private final CitaRepository citaRepository;

    /**
     * Constructor de DisponibilidadMedicoService.
     */
    public DisponibilidadMedicoService(DisponibilidadMedicoRepository disponibilidadMedicoRepository,
            CitaRepository citaRepository, BloqueosAgenda bloqueos, TiempoAgenda tiempo) {
        this.disponibilidadMedicoRepository = disponibilidadMedicoRepository;
        this.bloqueos = bloqueos;
        this.tiempo = tiempo;
        this.citaRepository = citaRepository;
    }

    /**
     * Lista todas las disponibilidades médicas registradas.
     */
    public List<DisponibilidadMedico> listarDisponibilidades() {
        return disponibilidadMedicoRepository.findAll();
    }

    /**
     * Lista las disponibilidades médicas asociadas a un médico.
     */
    public List<DisponibilidadMedico> listarPorMedico(Long medicoId) {
        return disponibilidadMedicoRepository.findByMedicoId(medicoId);
    }

    /**
     * Obtiene una disponibilidad médica por su identificador.
     */
    public DisponibilidadMedico buscarPorId(Long id) {
        return disponibilidadMedicoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro la disponibilidad."));
    }

    /**
     * Crea una nueva disponibilidad médica después de validar el horario.
     */
    @Transactional
    public DisponibilidadMedico crearDisponibilidad(Long medicoId, LocalDate fecha, LocalTime horaInicio,
            LocalTime horaFin) {
        Medico medico = bloqueos.medico(medicoId);

        if (!medico.isActivo()) {
            throw new IllegalArgumentException("El medico no esta activo.");
        }

        validarHorario(fecha, horaInicio, horaFin);
        validarHorarioFuturo(fecha, horaInicio);

        if (disponibilidadMedicoRepository
                .existsByMedicoIdAndFechaAndHoraInicioAndHoraFin(
                        medicoId, fecha, horaInicio, horaFin)) {
            throw new IllegalArgumentException(
                    "El médico ya tiene una disponibilidad registrada en esa fecha y horario.");
        }

        validarCruceHorario(medicoId, fecha, horaInicio, horaFin, null);

        DisponibilidadMedico disponibilidad = new DisponibilidadMedico(medico, fecha, horaInicio, horaFin);

        return disponibilidadMedicoRepository.save(disponibilidad);
    }

    /**
     * Actualiza una disponibilidad médica manteniendo las validaciones de horario.
     */
    @Transactional
    public DisponibilidadMedico actualizarDisponibilidad(Long id, LocalDate fecha, LocalTime horaInicio,
            LocalTime horaFin) {
        DisponibilidadMedico disponibilidad = bloqueos.disponibilidad(id);

        if (citaRepository.existsByDisponibilidadId(id)) {
            throw new IllegalArgumentException("No se puede editar una disponibilidad con citas asociadas, incluso canceladas.");
        }

        validarHorario(fecha, horaInicio, horaFin);
        validarHorarioFuturo(fecha, horaInicio);

        Long medicoId = disponibilidad.getMedico().getId();

        if (disponibilidadMedicoRepository
                .existsByMedicoIdAndFechaAndHoraInicioAndHoraFinAndIdNot(
                        medicoId, fecha, horaInicio, horaFin, id)) {
            throw new IllegalArgumentException(
                    "El médico ya tiene una disponibilidad registrada en esa fecha y horario.");
        }

        validarCruceHorario(medicoId, fecha, horaInicio, horaFin, id);

        disponibilidad.setFecha(fecha);
        disponibilidad.setHoraInicio(horaInicio);
        disponibilidad.setHoraFin(horaFin);

        return disponibilidadMedicoRepository.save(disponibilidad);
    }

    /**
     * Elimina una disponibilidad médica sin citas asociadas.
     */
    @Transactional
    public void eliminarDisponibilidad(Long id) {
        DisponibilidadMedico disponibilidad = bloqueos.disponibilidad(id);

        if (citaRepository.existsByDisponibilidadId(id)) {
            throw new IllegalArgumentException("No se puede eliminar una disponibilidad con citas asociadas.");
        }

        disponibilidadMedicoRepository.delete(disponibilidad);
    }

    /**
     * Valida que el horario de la disponibilidad tenga datos válidos.
     */
    private void validarHorario(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha es obligatoria.");
        }

        if (horaInicio == null || horaFin == null) {
            throw new IllegalArgumentException("La hora de inicio y fin son obligatorias.");
        }

        if (!TiempoAgenda.minutoExacto(horaInicio) || !TiempoAgenda.minutoExacto(horaFin)) {
            throw new IllegalArgumentException("Los limites del horario no pueden tener segundos ni nanosegundos.");
        }
        if (!horaFin.isAfter(horaInicio)) {
            throw new IllegalArgumentException("La hora final debe ser despues de la hora inicial.");
        }
    }

    /**
     * Valida que la disponibilidad se registre en una fecha y hora futura.
     */
    private void validarHorarioFuturo(LocalDate fecha, LocalTime horaInicio) {
        LocalDateTime inicioDisponibilidad = LocalDateTime.of(fecha, horaInicio);

        if (!inicioDisponibilidad.isAfter(tiempo.ahora())) {
            throw new IllegalArgumentException(
                    "La disponibilidad debe registrarse en una fecha y hora futura.");
        }
    }

    /**
     * Valida que no exista otra disponibilidad que se cruce con el horario indicado.
     */
    private void validarCruceHorario(Long medicoId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
            Long idExcluir) {
        // La consulta escalar relee la BD; no valida contra entidades antiguas de la caché JPA.
        boolean existeCruceHorario = disponibilidadMedicoRepository.existeCruce(
                medicoId, fecha, horaInicio, horaFin, idExcluir);

        if (existeCruceHorario) {
            throw new IllegalArgumentException(
                    "El médico ya tiene una disponibilidad que se cruza con ese horario.");
        }
    }
}
