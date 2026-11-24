package com.CitasHospital.Citas.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.CitasHospital.Citas.model.Usuario;
import com.CitasHospital.Citas.repository.UsuarioRepository;

/**
 * Contiene la lógica de negocio relacionada con la autenticación de usuarios.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UsuarioRepository usuarioRepository;

    /**
     * Constructor de CustomUserDetailsService.
     */
    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Obtiene los datos de seguridad del usuario a partir de su correo.
     */
    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException("No se encontro el usuario."));

        if (!usuario.isActivo()) {
            throw new UsernameNotFoundException("El usuario esta inactivo.");
        }

        return new com.CitasHospital.Citas.security.UsuarioAutenticado(usuario);
    }
}
