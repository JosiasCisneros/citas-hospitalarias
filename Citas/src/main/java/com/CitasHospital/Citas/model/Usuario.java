package com.CitasHospital.Citas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Representa un usuario registrado en el sistema.
 */
@Entity
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Column(unique = true)
    private String cedula;

    @Column(unique = true)
    private String correo;

    private String contacto;
    private String password;

    @Enumerated(EnumType.STRING)
    private Rol rol;

    private boolean activo = true;
    // Columna heredada, ya no se consulta para recuperar cuentas. Migración futura.
    private String tokenRecuperacion;

    @Column(nullable = false, updatable = false, columnDefinition = "bigint default 0")
    private long versionRecuperacion;

    public long getVersionRecuperacion() { return versionRecuperacion; }

    /**
     * Constructor de Usuario.
     */
    public Usuario() {
    }

    /**
     * Constructor de Usuario.
     */
    public Usuario(String nombre, String cedula, String correo, String contacto, String password, Rol rol) {
        this.nombre = nombre;
        this.cedula = cedula;
        this.correo = correo;
        this.contacto = contacto;
        this.password = password;
        this.rol = rol;
    }

    /**
     * Obtiene el identificador del usuario.
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el identificador del usuario.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Obtiene el nombre del usuario.
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * Establece el nombre del usuario.
     */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /**
     * Obtiene la cédula del usuario.
     */
    public String getCedula() {
        return cedula;
    }

    /**
     * Establece la cédula del usuario.
     */
    public void setCedula(String cedula) {
        this.cedula = cedula;
    }

    /**
     * Obtiene el correo del usuario.
     */
    public String getCorreo() {
        return correo;
    }

    /**
     * Establece el correo del usuario.
     */
    public void setCorreo(String correo) {
        this.correo = correo;
    }

    /**
     * Obtiene el contacto del usuario.
     */
    public String getContacto() {
        return contacto;
    }

    /**
     * Establece el contacto del usuario.
     */
    public void setContacto(String contacto) {
        this.contacto = contacto;
    }

    /**
     * Obtiene la contraseña del usuario.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Establece la contraseña del usuario.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Obtiene el rol del usuario.
     */
    public Rol getRol() {
        return rol;
    }

    /**
     * Establece el rol del usuario.
     */
    public void setRol(Rol rol) {
        this.rol = rol;
    }

    /**
     * Indica si el usuario está activo.
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Establece si el usuario está activo.
     */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /**
     * Obtiene el token de recuperación del usuario.
     */
    public String getTokenRecuperacion() {
        return tokenRecuperacion;
    }

    /**
     * Establece el token de recuperación del usuario.
     */
    public void setTokenRecuperacion(String tokenRecuperacion) {
        this.tokenRecuperacion = tokenRecuperacion;
    }
}
