package com.CitasHospital.Citas.form;

/** Contrato público de registro: únicamente datos personales y contraseña. */
public class RegistroFormulario extends DatosUsuarioFormulario {
    private String password;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
