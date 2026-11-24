package com.CitasHospital.Citas.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Representa un médico registrado en el sistema.
 */
@Entity
public class Medico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String especialidad;
    private boolean activo = true;

    /**
     * Constructor de Medico.
     */
    public Medico() {
    }

    /**
     * Constructor de Medico.
     */
    public Medico(String nombre, String especialidad) {
        this.nombre = nombre;
        this.especialidad = especialidad;
    }

    /**
     * Obtiene el identificador del médico.
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el identificador del médico.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Obtiene el nombre del médico.
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * Establece el nombre del médico.
     */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /**
     * Obtiene la especialidad del médico.
     */
    public String getEspecialidad() {
        return especialidad;
    }

    /**
     * Establece la especialidad del médico.
     */
    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    /**
     * Indica si el médico está activo.
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Establece si el médico está activo.
     */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
