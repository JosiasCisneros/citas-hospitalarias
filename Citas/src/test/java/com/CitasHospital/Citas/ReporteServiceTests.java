package com.CitasHospital.Citas;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.CitasHospital.Citas.model.*;
import com.CitasHospital.Citas.service.ReporteService;
import static org.assertj.core.api.Assertions.*;

class ReporteServiceTests {
    final ReporteService reporte = new ReporteService();
    Cita cita(String nombre) {
        var usuario = new Usuario(nombre,"ID,\"sintético\"","csv@example.test","",null,Rol.USUARIO);
        var medico = new Medico("Médica 日本語","General");
        var fecha = LocalDate.of(2030,4,11);
        var disponibilidad = new DisponibilidadMedico(medico,fecha,LocalTime.of(9,10),LocalTime.of(10,10));
        var cita = new Cita(usuario,medico,disponibilidad,fecha,LocalTime.of(9,10),LocalTime.of(9,40),EstadoCita.CONFIRMADA);
        cita.setId(1L);
        return cita;
    }
    @ParameterizedTest
    @ValueSource(strings={"=1+1","+SUM(1,2)","-1+2","@SUM(1)","  \u0001=1+1","\u00a0\u200b@SUM(1)","\ttexto"," \r\n=1","＝1+1"})
    void prefijosPeligrososSeNeutralizanSinPerderContenido(String valor) {
        String csv = new String(reporte.generarCsv(List.of(cita(valor))),StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'"+valor.replace("\"","\"\"")+"\"");
    }
    @Test void conservaCsvUnicodeComillasSaltosYExportacionCompleta() {
        String nombre="Ana, \"María\"\n日本語";
        String csv = new String(reporte.generarCsv(List.of(cita(nombre),cita("Paciente 2"))),StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFID,Paciente,Cédula,Médico,Especialidad,Fecha,Hora inicio,Hora fin,Estado\n");
        assertThat(csv).contains("\"1\",\"Ana, \"\"María\"\"\n日本語\",\"ID,\"\"sintético\"\"\"");
        assertThat(csv).contains("\"Médica 日本語\",\"General\",\"11/04/2030\",\"09:10\",\"09:40\",\"CONFIRMADA\"\n");
        assertThat(csv).contains("\"1\",\"Paciente 2\"");
        assertThat(csv).doesNotContain("'Ana","'Paciente","'1\"");
    }
}
