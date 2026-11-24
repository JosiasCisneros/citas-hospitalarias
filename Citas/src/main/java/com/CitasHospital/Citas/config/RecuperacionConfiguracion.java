package com.CitasHospital.Citas.config;

import java.time.Clock;
import org.springframework.context.annotation.*;
import com.CitasHospital.Citas.service.CorreoRecuperacion;

@Configuration
public class RecuperacionConfiguracion {
    @Bean
    public Clock relojRecuperacion() { return Clock.systemUTC(); }

    @Bean @Profile("!recuperacion-local")
    public CorreoRecuperacion correoNoConfigurado() {
        return new CorreoRecuperacion() {
            public boolean disponible() { return false; }
            public void enviar(String destinatario, String enlace) {
                throw new IllegalStateException("Canal de recuperacion no configurado.");
            }
        };
    }
}
