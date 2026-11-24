package com.CitasHospital.Citas.service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.CitasHospital.Citas.model.Cita;
import com.CitasHospital.Citas.model.EstadoCita;

/**
 * Contiene la lógica de negocio relacionada con la generación de reportes.
 */
@Service
public class ReporteService {
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Calcula los totales por estado a partir de las citas previamente filtradas.
     */
    public Map<EstadoCita, Long> contarPorEstado(List<Cita> citas) {
        Map<EstadoCita, Long> resumen = new EnumMap<>(EstadoCita.class);

        for (EstadoCita estado : EstadoCita.values()) {
            resumen.put(estado, 0L);
        }

        for (Cita cita : citas) {
            resumen.computeIfPresent(cita.getEstado(), (estado, total) -> total + 1);
        }

        return resumen;
    }

    /**
     * Genera un archivo CSV a partir de las citas previamente filtradas.
     */
    public byte[] generarCsv(List<Cita> citas) {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("ID,Paciente,Cédula,Médico,Especialidad,Fecha,Hora inicio,Hora fin,Estado\n");

        for (Cita cita : citas) {
            csv.append(campo(cita.getId()))
                    .append(',')
                    .append(campo(cita.getUsuario().getNombre()))
                    .append(',')
                    .append(campo(cita.getUsuario().getCedula()))
                    .append(',')
                    .append(campo(cita.getMedico().getNombre()))
                    .append(',')
                    .append(campo(cita.getMedico().getEspecialidad()))
                    .append(',')
                    .append(campo(cita.getFecha().format(FORMATO_FECHA)))
                    .append(',')
                    .append(campo(cita.getHoraInicio().format(FORMATO_HORA)))
                    .append(',')
                    .append(campo(cita.getHoraFin().format(FORMATO_HORA)))
                    .append(',')
                    .append(campo(cita.getEstado()))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Escapa CSV y trata como texto los prefijos que una hoja puede evaluar.
     */
    private String campo(Object valor) {
        String texto = valor == null ? "" : valor.toString();
        if (valor instanceof String && posibleFormula(texto)) {
            texto = "'" + texto;
        }
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }

    private boolean posibleFormula(String texto) {
        int i = 0;
        while (i < texto.length()) {
            int c = texto.codePointAt(i);
            // Tab/CR/LF iniciales tambien tienen significado especial para importadores.
            if (c == '\t' || c == '\r' || c == '\n') { return true; }
            if (!(Character.isWhitespace(c) || Character.isSpaceChar(c)
                    || Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) {
                return "=+-@＝＋－＠".indexOf(c) >= 0;
            }
            i += Character.charCount(c);
        }
        return false;
    }

}
