package com.CitasHospital.Citas.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.CitasHospital.Citas.model.Cita;
import com.CitasHospital.Citas.model.EspacioCitaDisponible;
import com.CitasHospital.Citas.model.EstadoCita;
import com.CitasHospital.Citas.model.HorariosMedicoDisponible;
import com.CitasHospital.Citas.service.CitaService;
import com.CitasHospital.Citas.service.MedicoService;
import com.CitasHospital.Citas.service.TiempoAgenda;

/**
 * Gestiona las solicitudes relacionadas con las citas.
 */
@Controller
public class CitaController {
    private final CitaService citaService;
    private final MedicoService medicoService;
    private final TiempoAgenda tiempo;

    /**
     * Constructor de CitaController.
     */
    public CitaController(CitaService citaService, MedicoService medicoService, TiempoAgenda tiempo) {
        this.citaService = citaService;
        this.medicoService = medicoService;
        this.tiempo = tiempo;
    }

    /**
     * Lista los bloques disponibles para reservar citas.
     */
    @GetMapping("/citas/disponibles")
    public String listarEspaciosDisponibles(Model model) {
        List<EspacioCitaDisponible> espacios = citaService.listarEspaciosDisponibles();

        model.addAttribute("espaciosPorMedico", agruparEspaciosPorMedicoYFecha(espacios));
        return "citas-disponibles";
    }

    /**
     * Reserva un bloque seleccionado por el usuario autenticado.
     */
    @PostMapping("/citas/reservar/{disponibilidadId}")
    public String reservar(@PathVariable Long disponibilidadId, Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
            RedirectAttributes redirectAttributes) {
        try {
            citaService.reservarCita(principal.getName(), disponibilidadId, horaInicio);
            redirectAttributes.addFlashAttribute("exito",
                    "Reserva creada correctamente. La cita se encuentra pendiente.");
            return "redirect:/citas/mis-citas";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/citas/disponibles";
        }
    }

    /**
     * Lista las citas del usuario autenticado.
     */
    @GetMapping("/citas/mis-citas")
    public String listarMisCitas(Principal principal, Model model) {
        List<Cita> citas = citaService.listarHistorialUsuario(principal.getName());
        LocalDateTime ahora = tiempo.ahora();
        Set<Long> cancelables = citas.stream()
                .filter(cita -> cita.getEstado() == EstadoCita.PENDIENTE || cita.getEstado() == EstadoCita.CONFIRMADA)
                .filter(cita -> LocalDateTime.of(cita.getFecha(), cita.getHoraInicio()).isAfter(ahora))
                .map(Cita::getId).collect(Collectors.toSet());
        model.addAttribute("citas", citas);
        model.addAttribute("citasCancelables", cancelables);
        return "mis-citas";
    }

    /**
     * Cancela una cita del usuario autenticado.
     */
    @PostMapping("/citas/cancelar/{citaId}")
    public String cancelarCitaUsuario(@PathVariable Long citaId, Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            citaService.cancelarCitaUsuario(citaId, principal.getName());
            redirectAttributes.addFlashAttribute("exito", "Cita cancelada correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/citas/mis-citas";
    }

    /**
     * Filtra las citas para la administración.
     */
    @GetMapping("/admin/citas")
    public String listarCitasAdmin(@RequestParam(required = false) EstadoCita estado,
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) String especialidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            Model model) {
        List<Cita> citas;

        try {
            citas = citaService.filtrarCitas(estado, medicoId, especialidad, fechaDesde, fechaHasta);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            citas = citaService.listarTodas();
        }

        cargarModeloAdmin(model, citas, estado, medicoId, especialidad, fechaDesde, fechaHasta);
        return "citas-admin";
    }

    /**
     * Confirma una cita desde la administración.
     */
    @PostMapping("/admin/citas/confirmar/{citaId}")
    public String confirmarCita(@PathVariable Long citaId, RedirectAttributes redirectAttributes) {
        try {
            citaService.confirmarCita(citaId);
            redirectAttributes.addFlashAttribute("exito", "Cita confirmada correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/citas";
    }

    /**
     * Cancela una cita desde la administración.
     */
    @PostMapping("/admin/citas/cancelar/{citaId}")
    public String cancelarCitaAdministrador(@PathVariable Long citaId, RedirectAttributes redirectAttributes) {
        try {
            citaService.cancelarCitaAdministrador(citaId);
            redirectAttributes.addFlashAttribute("exito", "Cita cancelada correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/citas";
    }

    /**
     * Carga los datos necesarios para la vista administrativa de citas.
     */
    private void cargarModeloAdmin(Model model, List<Cita> citas, EstadoCita estado, Long medicoId,
            String especialidad, LocalDate fechaDesde, LocalDate fechaHasta) {
        model.addAttribute("citas", citas);
        LocalDateTime ahora = tiempo.ahora();
        Set<Long> confirmables = citas.stream()
                .filter(cita -> cita.getEstado() == EstadoCita.PENDIENTE)
                .filter(cita -> LocalDateTime.of(cita.getFecha(), cita.getHoraInicio()).isAfter(ahora))
                .map(Cita::getId).collect(Collectors.toSet());
        model.addAttribute("citasConfirmables", confirmables);
        model.addAttribute("estados", EstadoCita.values());
        model.addAttribute("medicos", medicoService.listarMedicos());
        model.addAttribute("estado", estado);
        model.addAttribute("medicoId", medicoId);
        model.addAttribute("especialidad", especialidad);
        model.addAttribute("fechaDesde", fechaDesde);
        model.addAttribute("fechaHasta", fechaHasta);
    }

    /**
     * Agrupa los bloques disponibles por médico y fecha para mostrarlos en la vista.
     */
    private List<HorariosMedicoDisponible> agruparEspaciosPorMedicoYFecha(List<EspacioCitaDisponible> espacios) {
        Map<Long, HorariosMedicoDisponible> grupos = new LinkedHashMap<>();

        espacios.stream()
                .sorted(Comparator.comparing(
                        (EspacioCitaDisponible espacio) -> espacio.getMedico().getNombre(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(EspacioCitaDisponible::getFecha)
                        .thenComparing(EspacioCitaDisponible::getHoraInicio))
                .forEach(espacio -> {
                    Long medicoId = espacio.getMedico().getId();
                    HorariosMedicoDisponible grupo = grupos.computeIfAbsent(
                            medicoId,
                            id -> new HorariosMedicoDisponible(espacio.getMedico(), new LinkedHashMap<>()));

                    grupo.getEspaciosPorFecha()
                            .computeIfAbsent(espacio.getFecha(), fecha -> new ArrayList<>())
                            .add(espacio);
                });

        return new ArrayList<>(grupos.values());
    }
}
