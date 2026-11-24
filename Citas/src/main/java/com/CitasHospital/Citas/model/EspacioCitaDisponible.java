package com.CitasHospital.Citas.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Representa un bloque disponible para reservar una cita.
 */
public class EspacioCitaDisponible {
    private Long disponibilidadId;
    private Medico medico;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    /**
     * Constructor de EspacioCitaDisponible.
     */
    public EspacioCitaDisponible(Long disponibilidadId, Medico medico, LocalDate fecha,
            LocalTime horaInicio, LocalTime horaFin) {
        this.disponibilidadId = disponibilidadId;
        this.medico = medico;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    /**
     * Obtiene el identificador de la disponibilidad.
     */
    public Long getDisponibilidadId() {
        return disponibilidadId;
    }

    /**
     * Establece el identificador de la disponibilidad.
     */
    public void setDisponibilidadId(Long disponibilidadId) {
        this.disponibilidadId = disponibilidadId;
    }

    /**
     * Obtiene el médico asociado al bloque.
     */
    public Medico getMedico() {
        return medico;
    }

    /**
     * Establece el médico asociado al bloque.
     */
    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    /**
     * Obtiene la fecha del bloque.
     */
    public LocalDate getFecha() {
        return fecha;
    }

    /**
     * Establece la fecha del bloque.
     */
    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    /**
     * Obtiene la hora de inicio del bloque.
     */
    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    /**
     * Establece la hora de inicio del bloque.
     */
    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    /**
     * Obtiene la hora de fin del bloque.
     */
    public LocalTime getHoraFin() {
        return horaFin;
    }

    /**
     * Establece la hora de fin del bloque.
     */
    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }
}
