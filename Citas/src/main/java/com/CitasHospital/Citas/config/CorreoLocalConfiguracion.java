package com.CitasHospital.Citas.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import com.CitasHospital.Citas.service.CorreoRecuperacion;

/** Solo SMTP de captura en loopback. No contiene credenciales ni configura relays. */
@Configuration @Profile("recuperacion-local")
public class CorreoLocalConfiguracion {
    @Bean
    public CorreoRecuperacion correoLocal(@Value("${recuperacion.smtp.puerto:1025}") int puerto) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(puerto);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "3000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "3000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "3000");
        return new CorreoRecuperacion() {
            public boolean disponible() { return true; }
            public void enviar(String destinatario, String enlace) {
                if (!destinatario.toLowerCase(Locale.ROOT).endsWith("@example.test")) {
                    throw new MailSendException("El buzon local solo admite cuentas sinteticas.");
                }
                SimpleMailMessage mensaje = new SimpleMailMessage();
                mensaje.setFrom("no-reply@example.test");
                mensaje.setTo(destinatario);
                mensaje.setSubject("Recuperacion de acceso - prueba local");
                mensaje.setText("Para cambiar tu contrasena abre este enlace, valido durante 15 minutos y una sola vez:\n"
                        + enlace + "\nSi no lo solicitaste, ignora este correo.");
                sender.send(mensaje);
            }
        };
    }
}
