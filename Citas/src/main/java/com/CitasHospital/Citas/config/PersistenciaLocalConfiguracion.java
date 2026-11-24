package com.CitasHospital.Citas.config;

import java.nio.file.Path;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Rechaza rutas ambiguas antes de migrar; nunca adopta ni borra una base desconocida. */
@Configuration
@Profile("persistencia-local")
public class PersistenciaLocalConfiguracion {
    @Bean
    public FlywayMigrationStrategy migrarBaseLocal(Environment entorno) {
        return flyway -> {
            String carpeta = entorno.getRequiredProperty("citas.datos");
            if (carpeta.isBlank() || carpeta.contains(";") || !Path.of(carpeta).isAbsolute()) {
                throw new IllegalArgumentException("CITAS_DATOS debe ser una carpeta absoluta sin punto y coma. Usa scripts/ejecutar-demo-local.ps1.");
            }
            try {
                flyway.migrate();
            } catch (FlywayException ex) {
                throw new IllegalStateException("No se pudo validar/migrar la base local. Deten el arranque; conserva una copia con la aplicacion detenida y revisa docs/CONFIGURACION_LOCAL.md. No habilites baseline ni borres datos para continuar.", ex);
            }
        };
    }
}
