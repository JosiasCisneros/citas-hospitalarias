package com.CitasHospital.Citas.service;

import java.nio.charset.StandardCharsets;

/** Política compartida por registro, edición y recuperación; límite real de BCrypt. */
public final class PoliticaPassword {
    private PoliticaPassword() { }
    public static void validar(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La contrasena es obligatoria.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contrasena no puede superar 72 bytes UTF-8.");
        }
    }
}
