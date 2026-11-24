package com.CitasHospital.Citas.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import com.CitasHospital.Citas.model.Usuario;

public class UsuarioAutenticado extends User {
    private final Long usuarioId;
    private final long versionRecuperacion;
    public UsuarioAutenticado(Usuario usuario) {
        super(usuario.getCorreo(), usuario.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
        this.usuarioId = usuario.getId();
        this.versionRecuperacion = usuario.getVersionRecuperacion();
    }
    public Long getUsuarioId() { return usuarioId; }
    public long getVersionRecuperacion() { return versionRecuperacion; }
}
