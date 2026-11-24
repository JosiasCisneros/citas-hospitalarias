package com.CitasHospital.Citas.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.CitasHospital.Citas.model.Cita;
import com.CitasHospital.Citas.model.EstadoCita;
import com.CitasHospital.Citas.service.CitaService;
import com.CitasHospital.Citas.service.MedicoService;
import com.CitasHospital.Citas.service.ReporteService;

/**
 * Gestiona las solicitudes relacionadas con los reportes.
 */
@Controller
public class ReporteController {
    private final CitaService citaService;
    private final MedicoService medicoService;
    private final ReporteService reporteService;

    /**
     * Constructor de ReporteController.
     */
    public ReporteController(CitaService citaService, MedicoService medicoService,
            ReporteService reporteService) {
        this.citaService = citaService;
        this.medicoService = medicoService;
        this.reporteService = reporteService;
    }

    /**
     * Filtra las citas y muestra el panel de reportes.
     */
    @GetMapping("/admin/reportes")
    public String mostrarReporte(@RequestParam(required = false) EstadoCita estado,
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
            citas = List.of();
        }

        cargarModelo(model, citas, estado, medicoId, especialidad, fechaDesde, fechaHasta);
        return "reportes-citas";
    }

    /**
     * Genera un archivo CSV con las citas filtradas.
     */
    @GetMapping("/admin/reportes/csv")
    public ResponseEntity<byte[]> exportarCsv(@RequestParam(required = false) EstadoCita estado,
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) String especialidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        List<Cita> citas = citaService.filtrarCitas(estado, medicoId, especialidad, fechaDesde, fechaHasta);
        byte[] contenido = reporteService.generarCsv(citas);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("reporte_citas.csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(contenido);
    }

    /**
     * Carga los datos necesarios para la vista de reportes.
     */
    private void cargarModelo(Model model, List<Cita> citas, EstadoCita estado, Long medicoId,
            String especialidad, LocalDate fechaDesde, LocalDate fechaHasta) {
        Map<EstadoCita, Long> resumen = reporteService.contarPorEstado(citas);

        model.addAttribute("citas", citas);
        model.addAttribute("totalCitas", citas.size());
        model.addAttribute("pendientes", resumen.get(EstadoCita.PENDIENTE));
        model.addAttribute("confirmadas", resumen.get(EstadoCita.CONFIRMADA));
        model.addAttribute("canceladas", resumen.get(EstadoCita.CANCELADA));
        model.addAttribute("estados", EstadoCita.values());
        model.addAttribute("medicos", medicoService.listarMedicos());
        model.addAttribute("especialidades", medicoService.listarEspecialidades());
        model.addAttribute("estado", estado);
        model.addAttribute("medicoId", medicoId);
        model.addAttribute("especialidad", especialidad);
        model.addAttribute("fechaDesde", fechaDesde);
        model.addAttribute("fechaHasta", fechaHasta);
    }
}
