package com.CitasHospital.Citas.service;

public interface CorreoRecuperacion {
    boolean disponible();
    void enviar(String destinatario, String enlace);
}
