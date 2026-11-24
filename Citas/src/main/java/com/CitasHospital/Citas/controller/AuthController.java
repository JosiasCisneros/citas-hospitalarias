package com.CitasHospital.Citas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.CitasHospital.Citas.form.RegistroFormulario;
import com.CitasHospital.Citas.service.UsuarioService;

/**
 * Gestiona las solicitudes relacionadas con la autenticación.
 */
@Controller
public class AuthController {
    private final UsuarioService usuarioService;

    /**
     * Constructor de AuthController.
     */
    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Redirige la raíz del sistema hacia el inicio de sesión.
     */
    @GetMapping("/")
    public String raiz() {
        return "redirect:/login";
    }

    /**
     * Muestra el formulario de inicio de sesión.
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Muestra el formulario de registro de usuario.
     */
    @GetMapping("/registro")
    public String mostrarRegistro(Model model) {
        model.addAttribute("usuario", new RegistroFormulario());
        return "registro";
    }

    /**
     * Crea un nuevo usuario desde el formulario de registro.
     */
    @PostMapping("/registro")
    public String registrarUsuario(@ModelAttribute("usuario") RegistroFormulario usuario, Model model) {
        try {
            usuarioService.registrarUsuario(usuario);
            return "redirect:/login?registroExitoso";
        } catch (IllegalArgumentException e) {
            usuario.setPassword(null);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("usuario", usuario);
            return "registro";
        }
    }

}
