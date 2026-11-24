package com.CitasHospital.Citas.form;

import com.CitasHospital.Citas.model.Usuario;

/** Campos editables de un usuario; no contiene identidad ni estado de seguridad. */
public class DatosUsuarioFormulario {
    private String nombre;
    private String cedula;
    private String correo;
    private String contacto;

    public static DatosUsuarioFormulario desde(Usuario usuario) {
        DatosUsuarioFormulario datos = new DatosUsuarioFormulario();
        datos.setNombre(usuario.getNombre());
        datos.setCedula(usuario.getCedula());
        datos.setCorreo(usuario.getCorreo());
        datos.setContacto(usuario.getContacto());
        return datos;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCedula() { return cedula; }
    public void setCedula(String cedula) { this.cedula = cedula; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
}
