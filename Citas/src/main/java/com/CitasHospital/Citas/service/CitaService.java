package com.CitasHospital.Citas.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.CitasHospital.Citas.model.Cita;
import com.CitasHospital.Citas.model.DisponibilidadMedico;
import com.CitasHospital.Citas.model.EspacioCitaDisponible;
import com.CitasHospital.Citas.model.EstadoCita;
import com.CitasHospital.Citas.model.Medico;
import com.CitasHospital.Citas.model.Usuario;
import com.CitasHospital.Citas.repository.CitaRepository;
import com.CitasHospital.Citas.repository.DisponibilidadMedicoRepository;
import com.CitasHospital.Citas.repository.UsuarioRepository;

/**
 * Contiene la lógica de negocio relacionada con la gestión de citas.
 */
@Service
public class CitaService {
    private static final int DURACION_BLOQUE_MINUTOS = 30;

    private static final List<EstadoCita> ESTADOS_ACTIVOS =
            List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA);

    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DisponibilidadMedicoRepository disponibilidadMedicoRepository;
    private final BloqueosAgenda bloqueos;
    private final TiempoAgenda tiempo;

    /**
     * Constructor de CitaService.
     */
    public CitaService(CitaRepository citaRepository, UsuarioRepository usuarioRepository,
            DisponibilidadMedicoRepository disponibilidadMedicoRepository, BloqueosAgenda bloqueos, TiempoAgenda tiempo) {
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
        this.disponibilidadMedicoRepository = disponibilidadMedicoRepository;
        this.bloqueos = bloqueos;
        this.tiempo = tiempo;
    }

    /**
     * Obtiene una cita por su identificador.
     */
    @Transactional(readOnly = true)
    public Cita buscarPorId(Long id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro la cita."));
    }

    /**
     * Lista todas las citas registradas.
     */
    @Transactional(readOnly = true)
    public List<Cita> listarTodas() {
        return citaRepository.findAllByOrderByFechaDescHoraInicioDesc();
    }

    /**
     * Filtra las citas utilizando únicamente los criterios proporcionados.
     */
    @Transactional(readOnly = true)
    public List<Cita> filtrarCitas(EstadoCita estado, Long medicoId, String especialidad, LocalDate fechaDesde,
            LocalDate fechaHasta) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("El rango de fechas no es valido.");
        }

        return listarTodas()
                .stream()
                .filter(cita -> estado == null || cita.getEstado() == estado)
                .filter(cita -> medicoId == null
                        || (cita.getMedico() != null && cita.getMedico().getId().equals(medicoId)))
                .filter(cita -> especialidad == null || especialidad.isBlank()
                        || (cita.getMedico() != null
                                && cita.getMedico().getEspecialidad() != null
                                && cita.getMedico().getEspecialidad().toLowerCase()
                                        .contains(especialidad.toLowerCase())))
                .filter(cita -> fechaDesde == null || !cita.getFecha().isBefore(fechaDesde))
                .filter(cita -> fechaHasta == null || !cita.getFecha().isAfter(fechaHasta))
                .toList();
    }

    /**
     * Lista las citas del usuario autenticado.
     */
    @Transactional(readOnly = true)
    public List<Cita> listarHistorialUsuario(String correoUsuario) {
        validarCorreoUsuario(correoUsuario);

        Usuario usuario = buscarUsuarioPorCorreo(correoUsuario);

        return citaRepository.findByUsuarioIdOrderByFechaDescHoraInicioDesc(usuario.getId());
    }

    /**
     * Genera bloques disponibles de 30 minutos dentro de las disponibilidades médicas.
     */
    @Transactional(readOnly = true)
    public List<EspacioCitaDisponible> listarEspaciosDisponibles() {
        LocalDateTime ahora = tiempo.ahora();

        return disponibilidadMedicoRepository.findAll()
                .stream()
                .filter(this::disponibilidadBaseValida)
                .flatMap(disponibilidad -> generarEspaciosDisponibles(disponibilidad, ahora).stream())
                .sorted(Comparator.comparing(EspacioCitaDisponible::getFecha)
                        .thenComparing(EspacioCitaDisponible::getHoraInicio)
                        .thenComparing(espacio -> espacio.getMedico().getNombre(),
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /**
     * Reserva un bloque de 30 minutos dentro de una disponibilidad médica, validando el usuario,
     * el horario seleccionado, los cruces existentes y la disponibilidad del bloque.
     */
    @Transactional
    public Cita reservarCita(String correoUsuario, Long disponibilidadId, LocalTime horaInicio) {
        validarCorreoUsuario(correoUsuario);

        if (disponibilidadId == null) {
            throw new IllegalArgumentException("La disponibilidad es obligatoria.");
        }

        if (horaInicio == null) {
            throw new IllegalArgumentException("La hora de inicio es obligatoria.");
        }

        Usuario usuario = bloqueos.usuario(correoUsuario);
        if (!usuario.isActivo()) throw new IllegalArgumentException("El usuario no esta activo.");
        DisponibilidadMedico disponibilidad = bloqueos.disponibilidad(disponibilidadId);
        Medico medico = disponibilidad.getMedico();
        if (!medico.isActivo()) throw new IllegalArgumentException("El medico no esta activo.");

        LocalTime horaFin = horaInicio.plusMinutes(DURACION_BLOQUE_MINUTOS);

        validarBloqueSeleccionado(disponibilidad, horaInicio, horaFin);
        validarCruceHorario(usuario.getId(), disponibilidad.getFecha(), horaInicio, horaFin);

        // Vuelve a comprobar el bloque después de adquirir el bloqueo para evitar una reserva concurrente.
        if (citaRepository.existeCitaActivaMedicoEnHorario(medico.getId(), disponibilidad.getFecha(), horaInicio, horaFin, ESTADOS_ACTIVOS)) {
            throw new IllegalArgumentException("El espacio seleccionado ya no esta disponible.");
        }

        Cita cita = new Cita(usuario, medico, disponibilidad, disponibilidad.getFecha(),
                horaInicio, horaFin, EstadoCita.PENDIENTE);

        cita.setFechaCreacion(tiempo.ahora());
        return citaRepository.save(cita);
    }

    /**
     * Confirma una cita que se encuentra en estado pendiente.
     */
    @Transactional
    public Cita confirmarCita(Long citaId) {
        validarCitaId(citaId);

        Cita cita = bloqueos.cita(citaId);

        if (cita.getEstado() != EstadoCita.PENDIENTE) {
            throw new IllegalArgumentException("Solo se pueden confirmar citas pendientes.");
        }

        if (!LocalDateTime.of(cita.getFecha(), cita.getHoraInicio()).isAfter(tiempo.ahora())) {
            throw new IllegalArgumentException("No se puede confirmar una cita cuyo inicio ya ocurrio.");
        }
        cita.setEstado(EstadoCita.CONFIRMADA);

        return citaRepository.save(cita);
    }

    /**
     * Cancela una cita del usuario cuando todavía no ha iniciado.
     */
    @Transactional
    public Cita cancelarCitaUsuario(Long citaId, String correoUsuario) {
        validarCitaId(citaId);
        validarCorreoUsuario(correoUsuario);

        Cita cita = bloqueos.cita(citaId);

        Usuario usuario = buscarUsuarioPorCorreo(correoUsuario);

        if (!cita.getUsuario().getId().equals(usuario.getId())) {
            throw new IllegalArgumentException("No tiene permiso para cancelar esta cita.");
        }

        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new IllegalArgumentException("La cita ya se encuentra cancelada.");
        }

        LocalDateTime inicioCita = LocalDateTime.of(cita.getFecha(), cita.getHoraInicio());

        if (!inicioCita.isAfter(tiempo.ahora())) {
            throw new IllegalArgumentException("No se puede cancelar una cita cuya fecha y hora ya ocurrieron.");
        }

        return cancelarCita(cita);
    }

    /**
     * Cancela una cita desde la administración sin aplicar la restricción temporal del usuario.
     */
    @Transactional
    public Cita cancelarCitaAdministrador(Long citaId) {
        validarCitaId(citaId);

        Cita cita = bloqueos.cita(citaId);

        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new IllegalArgumentException("La cita ya se encuentra cancelada.");
        }

        return cancelarCita(cita);
    }

    /**
     * Cancela una cita conservando su historial.
     */
    private Cita cancelarCita(Cita cita) {
        cita.setEstado(EstadoCita.CANCELADA);

        return citaRepository.save(cita);
    }

    /**
     * Indica si la disponibilidad tiene los datos mínimos para generar bloques.
     */
    private boolean disponibilidadBaseValida(DisponibilidadMedico disponibilidad) {
        return disponibilidad != null
                && disponibilidad.getId() != null
                && disponibilidad.getMedico() != null
                && disponibilidad.getMedico().isActivo()
                && disponibilidad.getFecha() != null
                && disponibilidad.getHoraInicio() != null
                && disponibilidad.getHoraFin() != null
                && disponibilidad.getHoraFin().isAfter(disponibilidad.getHoraInicio())
                && TiempoAgenda.minutoExacto(disponibilidad.getHoraInicio())
                && TiempoAgenda.minutoExacto(disponibilidad.getHoraFin());
    }

    /**
     * Genera los bloques completos de 30 minutos que permanecen libres dentro de una disponibilidad.
     */
    private List<EspacioCitaDisponible> generarEspaciosDisponibles(DisponibilidadMedico disponibilidad,
            LocalDateTime ahora) {
        List<Cita> citasActivas = citaRepository.findByMedicoIdAndFechaAndEstadoIn(
                disponibilidad.getMedico().getId(), disponibilidad.getFecha(), ESTADOS_ACTIVOS);
        List<EspacioCitaDisponible> espacios = new ArrayList<>();
        LocalTime inicioBloque = disponibilidad.getHoraInicio();

        while (true) {
            LocalTime finBloque = inicioBloque.plusMinutes(DURACION_BLOQUE_MINUTOS);

            if (!finBloque.isAfter(inicioBloque) || finBloque.isAfter(disponibilidad.getHoraFin())) {
                break;
            }

            boolean bloqueFuturo = LocalDateTime.of(disponibilidad.getFecha(), inicioBloque).isAfter(ahora);
            boolean bloqueOcupado = existeCruce(citasActivas, inicioBloque, finBloque);

            if (bloqueFuturo && !bloqueOcupado) {
                espacios.add(new EspacioCitaDisponible(
                        disponibilidad.getId(),
                        disponibilidad.getMedico(),
                        disponibilidad.getFecha(),
                        inicioBloque,
                        finBloque));
            }

            inicioBloque = finBloque;
        }

        return espacios;
    }

    /**
     * Valida que el horario seleccionado se encuentre dentro de la disponibilidad médica.
     */
    private void validarBloqueSeleccionado(DisponibilidadMedico disponibilidad, LocalTime horaInicio,
            LocalTime horaFin) {
        if (!disponibilidadBaseValida(disponibilidad)) {
            throw new IllegalArgumentException("La disponibilidad seleccionada no es valida.");
        }

        if (horaInicio == null) {
            throw new IllegalArgumentException("La hora de inicio es obligatoria.");
        }

        if (!TiempoAgenda.minutoExacto(horaInicio)) {
            throw new IllegalArgumentException("La hora seleccionada no puede tener segundos ni nanosegundos.");
        }
        if (!horaFin.isAfter(horaInicio)
                || !Duration.between(horaInicio, horaFin).equals(Duration.ofMinutes(DURACION_BLOQUE_MINUTOS))) {
            throw new IllegalArgumentException("La cita debe durar exactamente 30 minutos.");
        }

        if (horaInicio.isBefore(disponibilidad.getHoraInicio())
                || horaFin.isAfter(disponibilidad.getHoraFin())) {
            throw new IllegalArgumentException("El bloque seleccionado esta fuera de la disponibilidad.");
        }

        Duration desplazamiento = Duration.between(disponibilidad.getHoraInicio(), horaInicio);
        if (desplazamiento.isNegative() || desplazamiento.toNanos() % Duration.ofMinutes(DURACION_BLOQUE_MINUTOS).toNanos() != 0) {
            throw new IllegalArgumentException("La hora seleccionada no corresponde a un bloque disponible.");
        }

        LocalDateTime inicioCita = LocalDateTime.of(disponibilidad.getFecha(), horaInicio);

        if (!inicioCita.isAfter(tiempo.ahora())) {
            throw new IllegalArgumentException("No se pueden reservar citas en fechas u horas pasadas.");
        }
    }

    /**
     * Valida que el usuario no tenga otra cita activa que se solape con el horario seleccionado.
     */
    private void validarCruceHorario(Long usuarioId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        boolean existeCruceHorario = citaRepository.existeCitaActivaUsuarioEnHorario(
                usuarioId, fecha, horaInicio, horaFin, ESTADOS_ACTIVOS);

        if (existeCruceHorario) {
            throw new IllegalArgumentException("El usuario ya tiene otra cita activa en ese horario.");
        }
    }

    /**
     * Indica si el bloque seleccionado se encuentra ocupado por una cita activa.
     */
    private boolean existeCruce(List<Cita> citasActivas, LocalTime horaInicio, LocalTime horaFin) {
        return citasActivas.stream()
                .anyMatch(cita -> horaInicio.isBefore(cita.getHoraFin())
                        && horaFin.isAfter(cita.getHoraInicio()));
    }

    /**
     * Obtiene un usuario por su correo.
     */
    private Usuario buscarUsuarioPorCorreo(String correoUsuario) {
        return usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario."));
    }

    /**
     * Valida que el correo del usuario esté presente.
     */
    private void validarCorreoUsuario(String correoUsuario) {
        if (correoUsuario == null || correoUsuario.isBlank()) {
            throw new IllegalArgumentException("El correo del usuario es obligatorio.");
        }
    }

    /**
     * Valida que el identificador de la cita esté presente.
     */
    private void validarCitaId(Long citaId) {
        if (citaId == null) {
            throw new IllegalArgumentException("La cita es obligatoria.");
        }
    }
}
