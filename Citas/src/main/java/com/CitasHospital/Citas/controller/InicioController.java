package com.CitasHospital.Citas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Gestiona las solicitudes relacionadas con la página de inicio.
 */
@Controller
public class InicioController {

    /**
     * Envia al administrador a su agenda y conserva el inicio del paciente.
     */
    @GetMapping("/inicio")
    public String inicio(Authentication authentication) {
        if (authentication.getAuthorities().stream()
                .anyMatch(autoridad -> "ROLE_ADMIN".equals(autoridad.getAuthority()))) {
            return "redirect:/admin/citas";
        }
        return "inicio";
    }
}
