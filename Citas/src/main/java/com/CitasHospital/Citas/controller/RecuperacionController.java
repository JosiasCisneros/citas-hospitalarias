package com.CitasHospital.Citas.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.CitasHospital.Citas.service.*;

@Controller
public class RecuperacionController {
    private final RecuperacionService servicio;
    private final LimiteRecuperacion limite;
    public RecuperacionController(RecuperacionService servicio, LimiteRecuperacion limite) {
        this.servicio = servicio; this.limite = limite;
    }
    private String mensaje() {
        return servicio.disponible()
                ? "Si la cuenta esta activa y la solicitud puede atenderse, recibiras un correo con instrucciones."
                : "La recuperacion no esta disponible en este entorno. No se ha enviado ningun correo.";
    }
    private void proteger(HttpServletResponse response) {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
    }
    @GetMapping("/recuperar-password")
    public String mostrar(Model model, HttpServletResponse response) {
        proteger(response);
        if (!servicio.disponible()) model.addAttribute("mensaje", mensaje());
        return "recuperar-password";
    }
    @PostMapping("/recuperar-password")
    public String solicitar(@RequestParam(defaultValue = "") String correo, Model model, HttpServletResponse response) {
        proteger(response);
        String direccion = correo.trim();
        if (direccion.length() <= 255 && limite.permitir(direccion)) {
            try { servicio.solicitar(direccion); }
            catch (MailException | IllegalArgumentException e) {
                // No registrar excepción SMTP: podría incluir destinatarios o contenido del mensaje.
            }
        }
        model.addAttribute("mensaje", mensaje());
        return "recuperar-password";
    }
    @GetMapping("/cambiar-password")
    public String formulario(HttpServletResponse response) {
        proteger(response);
        return "cambiar-password";
    }
    @PostMapping("/cambiar-password")
    public String cambiar(@RequestParam(defaultValue = "") String token,
            @RequestParam(defaultValue = "") String nuevaPassword, Model model, HttpServletResponse response) {
        proteger(response);
        try {
            if (servicio.consumir(token, nuevaPassword)) return "redirect:/login?passwordCambiada";
            model.addAttribute("error", "El enlace no es valido o ya no esta disponible. Solicita uno nuevo.");
        } catch (IllegalArgumentException e) { model.addAttribute("error", e.getMessage()); }
        // No reflejar token ni contraseña en la respuesta, tampoco en errores de validación.
        return "cambiar-password";
    }
}
