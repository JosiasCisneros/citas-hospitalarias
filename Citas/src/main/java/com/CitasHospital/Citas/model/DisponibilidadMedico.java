package com.CitasHospital.Citas.model;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Representa una disponibilidad horaria definida para un médico.
 */
@Entity
@Table(
    name = "disponibilidad_medico",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_disponibilidad_medico_horario",
            columnNames = {
                "medico_id",
                "fecha",
                "hora_inicio",
                "hora_fin"
            }
        )
    }
)
public class DisponibilidadMedico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /**
     * Constructor de DisponibilidadMedico.
     */
    public DisponibilidadMedico() {
    }

    /**
     * Constructor de DisponibilidadMedico.
     */
    public DisponibilidadMedico(Medico medico, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        this.medico = medico;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    /**
     * Obtiene el identificador de la disponibilidad.
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el identificador de la disponibilidad.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Obtiene el médico asociado a la disponibilidad.
     */
    public Medico getMedico() {
        return medico;
    }

    /**
     * Establece el médico asociado a la disponibilidad.
     */
    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    /**
     * Obtiene la fecha de la disponibilidad.
     */
    public LocalDate getFecha() {
        return fecha;
    }

    /**
     * Establece la fecha de la disponibilidad.
     */
    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    /**
     * Obtiene la hora de inicio de la disponibilidad.
     */
    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    /**
     * Establece la hora de inicio de la disponibilidad.
     */
    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    /**
     * Obtiene la hora de fin de la disponibilidad.
     */
    public LocalTime getHoraFin() {
        return horaFin;
    }

    /**
     * Establece la hora de fin de la disponibilidad.
     */
    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }
}
