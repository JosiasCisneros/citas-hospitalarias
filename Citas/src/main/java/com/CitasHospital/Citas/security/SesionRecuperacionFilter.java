package com.CitasHospital.Citas.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import com.CitasHospital.Citas.repository.UsuarioRepository;

/** Sello de autenticación compartido: recuperación, edición de credenciales y desactivación.
 * Rechaza la sesión en su siguiente acceso; no interrumpe peticiones ya iniciadas. */
public class SesionRecuperacionFilter extends OncePerRequestFilter {
    private final UsuarioRepository usuarios;
    public SesionRecuperacionFilter(UsuarioRepository usuarios) { this.usuarios = usuarios; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado principal) {
            long version = usuarios.versionRecuperacion(principal.getUsuarioId()).orElse(-1L);
            if (version != principal.getVersionRecuperacion()) {
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.sendRedirect(request.getContextPath() + "/login?sesionRestablecida");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
