package com.CitasHospital.Citas.service;

import java.net.URI;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.CitasHospital.Citas.model.TokenRecuperacion;
import com.CitasHospital.Citas.repository.*;

@Service
public class RecuperacionService {
    private final UsuarioRepository usuarios;
    private final TokenRecuperacionRepository tokens;
    private final PasswordEncoder encoder;
    private final CorreoRecuperacion correo;
    private final Clock reloj;
    private final String urlBase;

    public RecuperacionService(UsuarioRepository usuarios, TokenRecuperacionRepository tokens,
            PasswordEncoder encoder, CorreoRecuperacion correo, Clock reloj,
            @Value("${recuperacion.url-base:http://127.0.0.1:8080}") String urlBase) {
        this.usuarios = usuarios; this.tokens = tokens; this.encoder = encoder;
        this.correo = correo; this.reloj = reloj;
        URI uri = URI.create(urlBase);
        if (!"http".equals(uri.getScheme()) || !java.util.Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost())
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("La URL base de recuperacion local debe ser HTTP loopback.");
        }
        this.urlBase = urlBase.replaceAll("/+$", "");
    }
    public boolean disponible() { return correo.disponible(); }

    @Transactional
    public void solicitar(String direccion) {
        if (!correo.disponible()) return;
        var cuenta = usuarios.buscarPorCorreoConBloqueo(direccion);
        if (cuenta.isEmpty() || !cuenta.get().isActivo()) return;
        var usuario = cuenta.get();
        String token = TokensRecuperacion.nuevo();
        Instant vence = reloj.instant().plus(Duration.ofMinutes(15));
        var registro = tokens.findByUsuarioId(usuario.getId()).orElse(null);
        if (registro == null) registro = new TokenRecuperacion(usuario, TokensRecuperacion.hash(token), vence);
        else registro.reemplazar(TokensRecuperacion.hash(token), vence);
        tokens.saveAndFlush(registro);
        // Un fallo SMTP revierte el reemplazo: el token anterior sigue siendo válido.
        // El fragmento no viaja en GET ni en Referer; se envía después en el cuerpo POST.
        correo.enviar(direccion, urlBase + "/cambiar-password#token=" + token);
    }

    @Transactional
    public boolean consumir(String token, String nuevaPassword) {
        PoliticaPassword.validar(nuevaPassword);
        if (!TokensRecuperacion.formatoValido(token)) return false;
        String hash = TokensRecuperacion.hash(token);
        var usuarioId = tokens.usuarioPorHash(hash);
        if (usuarioId.isEmpty()) return false;
        // Mismo bloqueo para emisión y consumo; la segunda transacción relee el estado confirmado.
        var usuario = usuarios.buscarPorIdConBloqueo(usuarioId.get()).orElseThrow();
        var registro = tokens.findByUsuarioId(usuario.getId()).orElseThrow();
        Instant ahora = reloj.instant();
        if (!usuario.isActivo() || !hash.equals(registro.getHash()) || !registro.vigente(ahora)) return false;
        usuario.setPassword(encoder.encode(nuevaPassword));
        usuario.setTokenRecuperacion(null);
        registro.consumir(ahora);
        usuarios.save(usuario);
        tokens.save(registro);
        usuarios.incrementarVersionRecuperacion(usuario.getId());
        return true;
    }
}
