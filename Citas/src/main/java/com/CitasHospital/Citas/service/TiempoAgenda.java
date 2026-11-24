package com.CitasHospital.Citas.service;

import java.time.*;
import org.springframework.stereotype.Component;

/** Vista del único Clock de la aplicación; recuperación continúa usando Instant. */
@Component
public class TiempoAgenda {
    public static final ZoneId ZONA = ZoneId.of("America/Costa_Rica");
    private final Clock reloj;
    public TiempoAgenda(Clock reloj) { this.reloj = reloj; }
    public LocalDateTime ahora() { return LocalDateTime.now(reloj.withZone(ZONA)); }
    public static boolean minutoExacto(LocalTime hora) {
        return hora != null && hora.getSecond() == 0 && hora.getNano() == 0;
    }
}
