package com.CitasHospital.Citas.service;

import java.util.List;
import java.time.Clock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import com.CitasHospital.Citas.repository.TokenRecuperacionRepository;


import com.CitasHospital.Citas.form.DatosUsuarioFormulario;
import com.CitasHospital.Citas.form.RegistroFormulario;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.CitasHospital.Citas.model.Rol;
import com.CitasHospital.Citas.model.Usuario;
import com.CitasHospital.Citas.repository.UsuarioRepository;

/**
 * Contiene la lógica de negocio relacionada con la gestión de usuarios.
 */
@Service
public class UsuarioService {
    /** Resultado interno: el controlador usa el cambio efectivo calculado bajo bloqueo. */
    public record Actualizacion(Usuario usuario, boolean credencialesModificadas) { }
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRecuperacionRepository tokens;
    private final Clock reloj;
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Constructor de UsuarioService.
     */
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            TokenRecuperacionRepository tokens, Clock reloj) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.reloj = reloj;
    }

    /**
     * Lista todos los usuarios registrados.
     */
    public List<Usuario> listarUsuarios() {
        return usuarioRepository.findAll();
    }

    /**
     * Lista los usuarios activos.
     */
    public List<Usuario> listarUsuariosActivos() {
        return usuarioRepository.findByActivoTrue();
    }

    /**
     * Obtiene un usuario por su identificador.
     */
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario."));
    }

    /**
     * Obtiene un usuario por su correo.
     */
    public Usuario buscarPorCorreo(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario con ese correo."));
    }

    /**
     * Crea un nuevo usuario después de validar sus datos.
     */
    public Usuario registrarUsuario(RegistroFormulario datos) {
        validarUsuarioNuevo(datos);
        // Siempre una entidad nueva: el cliente no decide id, rol, activo ni token.
        Usuario usuario = new Usuario(datos.getNombre(), datos.getCedula(), datos.getCorreo(),
                datos.getContacto(), passwordEncoder.encode(datos.getPassword()), Rol.USUARIO);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    /**
     * Actualiza los datos de un usuario manteniendo las validaciones de unicidad.
     */
    @Transactional
    public Actualizacion actualizarUsuario(Long id, DatosUsuarioFormulario datosUsuario, String nuevaPassword) {
        Usuario usuario = bloquearActual(id);
        validarUsuarioEditado(usuario, datosUsuario);
        if (nuevaPassword != null && !nuevaPassword.isBlank()) {
            PoliticaPassword.validar(nuevaPassword);
        }

        boolean cambiaPassword = nuevaPassword != null && !nuevaPassword.isBlank();
        boolean cambiaCorreo = !usuario.getCorreo().equals(datosUsuario.getCorreo());
        usuario.setNombre(datosUsuario.getNombre());
        usuario.setCedula(datosUsuario.getCedula());
        usuario.setCorreo(datosUsuario.getCorreo());
        usuario.setContacto(datosUsuario.getContacto());

        if (nuevaPassword != null && !nuevaPassword.isBlank()) {
            usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        }

        usuarioRepository.save(usuario);
        if (cambiaCorreo || cambiaPassword) revocarCredenciales(usuario);
        return new Actualizacion(usuario, cambiaCorreo || cambiaPassword);
    }

    /**
     * Desactiva un usuario sin eliminar su información del sistema.
     */
    @Transactional
    public void desactivarUsuario(Long id) {
        Usuario usuario = bloquearActual(id);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        revocarCredenciales(usuario);
    }

    private Usuario bloquearActual(Long id) {
        Usuario usuario = usuarioRepository.buscarPorIdConBloqueo(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario."));
        // Una entidad preleída por OpenEntityManagerInView no debe restaurar credenciales antiguas.
        entityManager.refresh(usuario);
        return usuario;
    }

    private void revocarCredenciales(Usuario usuario) {
        usuario.setTokenRecuperacion(null);
        tokens.findByUsuarioId(usuario.getId()).filter(t -> t.getConsumidoEn() == null).ifPresent(t -> {
            // La misma marca hace inutilizable el token anulado, sin borrar su registro.
            t.consumir(reloj.instant());
            tokens.save(t);
        });
        // Flush de cuenta/token y sello forman parte de esta misma transacción.
        usuarioRepository.incrementarVersionRecuperacion(usuario.getId());
    }

    /**
     * Valida que los datos del usuario nuevo sean correctos y únicos.
     */
    private void validarUsuarioNuevo(RegistroFormulario usuario) {
        validarDatosBasicos(usuario);
        usuario.setCedula(CedulaNacional.normalizar(usuario.getCedula()));

        PoliticaPassword.validar(usuario.getPassword());

        if (usuarioRepository.existsByCorreo(usuario.getCorreo())) {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo.");
        }

        if (cedulaDuplicada(usuario.getCedula(), null)) {
            throw new IllegalArgumentException("Ya existe un usuario con esa cedula.");
        }
    }

    /**
     * Valida que los datos editados del usuario sean correctos y únicos.
     */
    private void validarUsuarioEditado(Usuario persistido, DatosUsuarioFormulario usuario) {
        validarDatosBasicos(usuario);
        Long id = persistido.getId();
        // La excepción depende del valor bloqueado en BD, nunca de un campo del cliente.
        boolean conservaCedula = java.util.Objects.equals(persistido.getCedula(), usuario.getCedula());
        if (!conservaCedula) usuario.setCedula(CedulaNacional.normalizar(usuario.getCedula()));

        if (usuarioRepository.existsByCorreoAndIdNot(usuario.getCorreo(), id)) {
            throw new IllegalArgumentException("Ya existe otro usuario con ese correo.");
        }

        if (!conservaCedula && cedulaDuplicada(usuario.getCedula(), id)) {
            throw new IllegalArgumentException("Ya existe otro usuario con esa cedula.");
        }
    }

    private boolean cedulaDuplicada(String cedula, Long id) {
        String conGuiones = cedula.substring(0, 1) + "-" + cedula.substring(1, 5) + "-" + cedula.substring(5);
        return usuarioRepository.existeCedulaEquivalente(cedula, conGuiones, id);
    }

    /**
     * Valida que los datos básicos del usuario estén presentes.
     */
    private void validarDatosBasicos(DatosUsuarioFormulario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("Los datos del usuario son obligatorios.");
        }

        if (usuario.getNombre() == null || usuario.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }

        if (usuario.getCedula() == null || usuario.getCedula().isBlank()) {
            throw new IllegalArgumentException("Introduce una cédula de 9 dígitos.");
        }

        if (usuario.getCorreo() == null || usuario.getCorreo().isBlank()) {
            throw new IllegalArgumentException("El correo es obligatorio.");
        }

        validarLongitud(usuario.getNombre(), "El nombre");
        validarLongitud(usuario.getCorreo(), "El correo");
        validarLongitud(usuario.getContacto(), "El contacto");

        if (!correoValido(usuario.getCorreo())) {
            throw new IllegalArgumentException("El correo no tiene un formato valido.");
        }
    }

    /** Valida el límite de las columnas String existentes. */
    private void validarLongitud(String valor, String campo) {
        // Las columnas String de Usuario tienen el límite JPA predeterminado de 255.
        if (valor != null && valor.length() > 255) {
            throw new IllegalArgumentException(campo + " no puede superar 255 caracteres.");
        }
    }

    private boolean correoValido(String correo) {
        return correo != null && correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}
