package com.CitasHospital.Citas.service;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Ventana deslizante local: 30 solicitudes globales y 3 por correo en 15 minutos. */
@Component
public class LimiteRecuperacion {
    private final Clock reloj;
    private final Deque<Instant> global = new ArrayDeque<>();
    private final Map<String, Deque<Instant>> cuentas = new HashMap<>();
    public LimiteRecuperacion(Clock reloj) { this.reloj = reloj; }
    public synchronized boolean permitir(String correo) {
        Instant limite = reloj.instant().minus(Duration.ofMinutes(15));
        global.removeIf(t -> !t.isAfter(limite));
        cuentas.values().forEach(q -> q.removeIf(t -> !t.isAfter(limite)));
        cuentas.values().removeIf(Deque::isEmpty);
        if (global.size() >= 30) return false;
        global.addLast(reloj.instant());
        String clave = TokensRecuperacion.hash(correo.toLowerCase(Locale.ROOT));
        Deque<Instant> cola = cuentas.computeIfAbsent(clave, k -> new ArrayDeque<>());
        if (cola.size() >= 3) return false;
        cola.addLast(reloj.instant());
        return true;
    }
}
