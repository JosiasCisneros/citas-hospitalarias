package com.CitasHospital.Citas.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.CitasHospital.Citas.security.UsuarioAutenticado;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.CitasHospital.Citas.model.Usuario;
import com.CitasHospital.Citas.form.DatosUsuarioFormulario;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.CitasHospital.Citas.service.UsuarioService;

/**
 * Gestiona las solicitudes relacionadas con los usuarios.
 */
@Controller
public class UsuarioController {
    private final UsuarioService usuarioService;

    /**
     * Constructor de UsuarioController.
     */
    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Muestra el perfil del usuario autenticado.
     */
    @GetMapping("/perfil")
    public String verPerfil(Model model, Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorId(idAutenticado(authentication));

        model.addAttribute("usuario", DatosUsuarioFormulario.desde(usuario));
        return "perfil";
    }

    /**
     * Actualiza el perfil del usuario autenticado.
     */
    @PostMapping("/perfil")
    public String actualizarPerfil(@ModelAttribute("usuario") DatosUsuarioFormulario usuario,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String nuevaPassword,
            Authentication authentication,
            HttpServletRequest request, HttpServletResponse response,
            Model model) {
        Long usuarioId = idAutenticado(authentication);
        if (id != null && !id.equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede editar otro perfil.");
        }

        try {
            var resultado = usuarioService.actualizarUsuario(usuarioId, usuario, nuevaPassword);
            Usuario usuarioActualizado = resultado.usuario();
            if (resultado.credencialesModificadas()) {
                return cerrarSesion(authentication, request, response);
            }
            model.addAttribute("mensaje", "Perfil actualizado correctamente.");
            model.addAttribute("usuario", DatosUsuarioFormulario.desde(usuarioActualizado));
            return "perfil";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("usuario", usuario);
            return "perfil";
        }
    }

    /**
     * Lista los usuarios registrados para administración.
     */
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarUsuarios());
        return "usuarios-lista";
    }

    /**
     * Muestra el formulario de edición de usuario.
     */
    @GetMapping("/usuarios/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioService.buscarPorId(id);

        model.addAttribute("usuario", DatosUsuarioFormulario.desde(usuario));
        model.addAttribute("usuarioId", id);
        return "usuario-formulario";
    }

    /**
     * Actualiza los datos de un usuario desde la administración.
     */
    @PostMapping("/usuarios/editar/{id}")
    public String actualizarUsuarioAdmin(@PathVariable Long id,
            @ModelAttribute("usuario") DatosUsuarioFormulario usuario,
            @RequestParam(required = false) String nuevaPassword,
            Authentication authentication, HttpServletRequest request, HttpServletResponse response,
            Model model) {
        boolean propio = id.equals(idAutenticado(authentication));
        try {
            var resultado = usuarioService.actualizarUsuario(id, usuario, nuevaPassword);
            if (propio && resultado.credencialesModificadas()) {
                return cerrarSesion(authentication, request, response);
            }
            return "redirect:/usuarios?actualizado";
        } catch (IllegalArgumentException e) {
            model.addAttribute("usuarioId", id);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("usuario", usuario);
            return "usuario-formulario";
        }
    }

    /**
     * Desactiva un usuario desde la administración.
     */
    @PostMapping("/usuarios/desactivar/{id}")
    public String desactivarUsuario(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        boolean propio = id.equals(idAutenticado(authentication));
        usuarioService.desactivarUsuario(id);
        if (propio) return cerrarSesion(authentication, request, response);
        return "redirect:/usuarios?desactivado";
    }

    private Long idAutenticado(Authentication authentication) {
        if (authentication.getPrincipal() instanceof UsuarioAutenticado principal) return principal.getUsuarioId();
        // Compatibilidad con identidades Spring Security genéricas; nunca viene del formulario.
        return usuarioService.buscarPorCorreo(authentication.getName()).getId();
    }

    private String cerrarSesion(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?sesionRestablecida";
    }
}
