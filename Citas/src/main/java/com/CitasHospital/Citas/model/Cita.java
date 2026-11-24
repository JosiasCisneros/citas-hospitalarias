package com.CitasHospital.Citas.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Representa una cita médica reservada por un usuario.
 */
@Entity
@Table(name = "cita")
public class Cita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @ManyToOne(optional = false)
    @JoinColumn(name = "disponibilidad_id", nullable = false)
    private DisponibilidadMedico disponibilidad;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoCita estado;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Constructor de Cita.
     */
    public Cita() {
    }

    /**
     * Constructor de Cita.
     */
    public Cita(Usuario usuario, Medico medico, DisponibilidadMedico disponibilidad, LocalDate fecha,
            LocalTime horaInicio, LocalTime horaFin, EstadoCita estado) {
        this.usuario = usuario;
        this.medico = medico;
        this.disponibilidad = disponibilidad;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.estado = estado;
        this.fechaCreacion = LocalDateTime.now();
    }

    @PrePersist
    protected void asignarFechaCreacion() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }

    /**
     * Obtiene el identificador de la cita.
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el identificador de la cita.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Obtiene el usuario asociado a la cita.
     */
    public Usuario getUsuario() {
        return usuario;
    }

    /**
     * Establece el usuario asociado a la cita.
     */
    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    /**
     * Obtiene el médico asociado a la cita.
     */
    public Medico getMedico() {
        return medico;
    }

    /**
     * Establece el médico asociado a la cita.
     */
    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    /**
     * Obtiene la disponibilidad asociada a la cita.
     */
    public DisponibilidadMedico getDisponibilidad() {
        return disponibilidad;
    }

    /**
     * Establece la disponibilidad asociada a la cita.
     */
    public void setDisponibilidad(DisponibilidadMedico disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    /**
     * Obtiene la fecha de la cita.
     */
    public LocalDate getFecha() {
        return fecha;
    }

    /**
     * Establece la fecha de la cita.
     */
    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    /**
     * Obtiene la hora de inicio de la cita.
     */
    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    /**
     * Establece la hora de inicio de la cita.
     */
    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    /**
     * Obtiene la hora de fin de la cita.
     */
    public LocalTime getHoraFin() {
        return horaFin;
    }

    /**
     * Establece la hora de fin de la cita.
     */
    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    /**
     * Obtiene el estado de la cita.
     */
    public EstadoCita getEstado() {
        return estado;
    }

    /**
     * Establece el estado de la cita.
     */
    public void setEstado(EstadoCita estado) {
        this.estado = estado;
    }

    /**
     * Obtiene la fecha de creación de la cita.
     */
    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /**
     * Establece la fecha de creación de la cita.
     */
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /**
     * Indica si la cita aún no ha iniciado.
     */
    @Transient
    public boolean isFutura() {
        return LocalDateTime.of(fecha, horaInicio)
                .isAfter(LocalDateTime.now());
    }
}
