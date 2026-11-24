package com.CitasHospital.Citas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.CitasHospital.Citas.model.Medico;
import com.CitasHospital.Citas.service.MedicoService;

/**
 * Gestiona las solicitudes relacionadas con los médicos.
 */
@Controller
public class MedicoController {
    private final MedicoService medicoService;

    @InitBinder("medico")
    public void limitarCampos(WebDataBinder binder) {
        binder.setAllowedFields("nombre", "especialidad");
    }

    /**
     * Constructor de MedicoController.
     */
    public MedicoController(MedicoService medicoService) {
        this.medicoService = medicoService;
    }

    /**
     * Lista los médicos registrados.
     */
    @GetMapping("/medicos")
    public String listarMedicos(Model model) {
        model.addAttribute("medicos", medicoService.listarMedicos());
        return "medicos-lista";
    }

    /**
     * Muestra el formulario de creación de médico.
     */
    @GetMapping("/medicos/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        model.addAttribute("medico", new Medico());
        model.addAttribute("titulo", "Registrar médico");
        return "medico-formulario";
    }

    /**
     * Crea un nuevo médico desde el formulario.
     */
    @PostMapping("/medicos/guardar")
    public String guardarMedico(@ModelAttribute("medico") Medico medico, Model model) {
        try {
            medicoService.guardarMedico(medico);
            return "redirect:/medicos?guardado";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("medico", medico);
            model.addAttribute("titulo", "Registrar médico");
            return "medico-formulario";
        }
    }

    /**
     * Muestra el formulario de edición de médico.
     */
    @GetMapping("/medicos/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable Long id, Model model) {
        Medico medico = medicoService.buscarPorId(id);

        model.addAttribute("medico", medico);
        model.addAttribute("titulo", "Editar médico");
        return "medico-formulario";
    }

    /**
     * Actualiza los datos de un médico.
     */
    @PostMapping("/medicos/editar/{id}")
    public String actualizarMedico(@PathVariable Long id, @ModelAttribute("medico") Medico medico, Model model) {
        try {
            medicoService.actualizarMedico(id, medico);
            return "redirect:/medicos?actualizado";
        } catch (IllegalArgumentException e) {
            medico.setId(id);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("medico", medico);
            model.addAttribute("titulo", "Editar médico");
            return "medico-formulario";
        }
    }

    /**
     * Desactiva un médico sin eliminar su información del sistema.
     */
    @PostMapping("/medicos/desactivar/{id}")
    public String desactivarMedico(@PathVariable Long id) {
        medicoService.desactivarMedico(id);
        return "redirect:/medicos?desactivado";
    }
}
