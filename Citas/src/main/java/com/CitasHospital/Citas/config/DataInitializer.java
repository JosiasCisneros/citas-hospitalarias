package com.CitasHospital.Citas.config;

import java.util.Locale;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.LoggerFactory;
import com.CitasHospital.Citas.model.Rol;
import com.CitasHospital.Citas.model.Usuario;
import com.CitasHospital.Citas.repository.UsuarioRepository;
import com.CitasHospital.Citas.service.PoliticaPassword;

/** Alta optativa de una cuenta ficticia. Nunca se ejecuta en el arranque normal. */
@Configuration
@Profile("demo-local")
public class DataInitializer {
    @Bean
    public CommandLineRunner crearAdministradorInicial(UsuarioRepository usuarios,
            PasswordEncoder encoder, Environment entorno) {
        return args -> {
            // Validar todo antes de escribir; errores sin valores ni contraseñas.
            String nombre = dato(entorno, "DEMO_ADMIN_NOMBRE");
            String cedula = dato(entorno, "DEMO_ADMIN_CEDULA");
            String correo = dato(entorno, "DEMO_ADMIN_CORREO");
            String password = entorno.getProperty("DEMO_ADMIN_PASSWORD", "");
            if (!correo.matches("^[^@\\s]+@[^@\\s]+$")
                    || !correo.toLowerCase(Locale.ROOT).endsWith("@example.test")) {
                throw new IllegalArgumentException("DEMO_ADMIN_CORREO debe ser una direccion sintetica @example.test.");
            }
            PoliticaPassword.validar(password);
            if (password.length() < 12) {
                throw new IllegalArgumentException("DEMO_ADMIN_PASSWORD debe tener al menos 12 caracteres y como maximo 72 bytes UTF-8.");
            }
            var existente = usuarios.findByCorreo(correo);
            if (existente.isPresent()) {
                if (existente.get().getRol() != Rol.ADMIN) {
                    throw new IllegalStateException("Inicializacion demo rechazada: el correo pertenece a una cuenta no administradora. No se modifico.");
                }
                LoggerFactory.getLogger(DataInitializer.class).info("Cuenta administradora de demo ya existente: se conserva intacta.");
                return;
            }
            if (usuarios.existsByCedula(cedula)) {
                throw new IllegalStateException("Inicializacion demo rechazada: la identificacion ya esta registrada. No se modifico ninguna cuenta.");
            }
            Usuario admin = new Usuario(nombre, cedula, correo, "Contacto ficticio",
                    encoder.encode(password), Rol.ADMIN);
            admin.setActivo(true);
            usuarios.save(admin);
            LoggerFactory.getLogger(DataInitializer.class).info("Cuenta administradora de demo creada; credenciales no registradas.");
        };
    }
    private static String dato(Environment entorno, String clave) {
        String valor = entorno.getProperty(clave, "").trim();
        if (valor.isEmpty() || valor.length() > 255) {
            throw new IllegalArgumentException("Configuracion demo incompleta o invalida: " + clave + " requiere entre 1 y 255 caracteres.");
        }
        return valor;
    }
}
