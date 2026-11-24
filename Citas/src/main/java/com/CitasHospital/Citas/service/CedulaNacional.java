package com.CitasHospital.Citas.service;

/** Solo formato nacional; no consulta ni acredita la identidad de una persona. */
public final class CedulaNacional {
    private CedulaNacional() { }

    public static String normalizar(String valor) {
        String cedula = valor == null ? "" : valor.strip();
        if (!cedula.matches("[0-9]{9}|[0-9]-[0-9]{4}-[0-9]{4}")) {
            throw new IllegalArgumentException("Introduce una cédula de 9 dígitos.");
        }
        return cedula.replace("-", "");
    }
}
