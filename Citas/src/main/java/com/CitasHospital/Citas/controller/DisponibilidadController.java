package com.CitasHospital.Citas.controller;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.CitasHospital.Citas.model.DisponibilidadMedico;
import com.CitasHospital.Citas.service.DisponibilidadMedicoService;
import com.CitasHospital.Citas.service.MedicoService;

/**
 * Gestiona las solicitudes relacionadas con las disponibilidades médicas.
 */
@Controller
public class DisponibilidadController {
    private final DisponibilidadMedicoService disponibilidadService;
    private final MedicoService medicoService;

    /**
     * Constructor de DisponibilidadController.
     */
    public DisponibilidadController(DisponibilidadMedicoService disponibilidadService, MedicoService medicoService) {
        this.disponibilidadService = disponibilidadService;
        this.medicoService = medicoService;
    }

    /**
     * Lista las disponibilidades médicas registradas.
     */
    @GetMapping("/disponibilidades")
    public String listarDisponibilidades(Model model) {
        model.addAttribute("disponibilidades", disponibilidadService.listarDisponibilidades());
        return "disponibilidades-lista";
    }

    /**
     * Muestra el formulario de creación de disponibilidad médica.
     */
    @GetMapping("/disponibilidades/nueva")
    public String mostrarFormularioNueva(Model model) {
        model.addAttribute("disponibilidad", new DisponibilidadMedico());
        model.addAttribute("medicos", medicoService.listarMedicosActivos());
        model.addAttribute("titulo", "Registrar disponibilidad");
        return "disponibilidad-formulario";
    }

    /**
     * Crea una nueva disponibilidad médica desde el formulario.
     */
    @PostMapping("/disponibilidades/guardar")
    public String guardarDisponibilidad(@RequestParam Long medicoId,
            @RequestParam LocalDate fecha,
            @RequestParam LocalTime horaInicio,
            @RequestParam LocalTime horaFin,
            Model model) {
        try {
            disponibilidadService.crearDisponibilidad(medicoId, fecha, horaInicio, horaFin);
            return "redirect:/disponibilidades?guardado";
        } catch (IllegalArgumentException e) {
            DisponibilidadMedico disponibilidad = new DisponibilidadMedico();
            disponibilidad.setFecha(fecha);
            disponibilidad.setHoraInicio(horaInicio);
            disponibilidad.setHoraFin(horaFin);

            model.addAttribute("error", e.getMessage());
            model.addAttribute("disponibilidad", disponibilidad);
            model.addAttribute("medicos", medicoService.listarMedicosActivos());
            model.addAttribute("titulo", "Registrar disponibilidad");
            return "disponibilidad-formulario";
        }
    }

    /**
     * Muestra el formulario de edición de disponibilidad médica.
     */
    @GetMapping("/disponibilidades/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable Long id, Model model, RedirectAttributes flash) {
        try {
            DisponibilidadMedico disponibilidad = disponibilidadService.buscarPorId(id);
            model.addAttribute("disponibilidad", disponibilidad);
            model.addAttribute("medicos", medicoService.listarMedicosActivos());
            model.addAttribute("titulo", "Editar disponibilidad");
            return "disponibilidad-formulario";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/disponibilidades";
        }
    }

    /**
     * Actualiza una disponibilidad médica desde el formulario.
     */
    @PostMapping("/disponibilidades/editar/{id}")
    public String actualizarDisponibilidad(@PathVariable Long id,
            @RequestParam LocalDate fecha, @RequestParam LocalTime horaInicio,
            @RequestParam LocalTime horaFin, RedirectAttributes flash) {
        try {
            disponibilidadService.actualizarDisponibilidad(id, fecha, horaInicio, horaFin);
            return "redirect:/disponibilidades?actualizado";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/disponibilidades";
        }
    }

    /**
     * Elimina una disponibilidad médica sin citas asociadas.
     */
    @PostMapping("/disponibilidades/eliminar/{id}")
    public String eliminarDisponibilidad(@PathVariable Long id, RedirectAttributes flash) {
        try {
            disponibilidadService.eliminarDisponibilidad(id);
            return "redirect:/disponibilidades?eliminado";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/disponibilidades";
        }
    }
}
