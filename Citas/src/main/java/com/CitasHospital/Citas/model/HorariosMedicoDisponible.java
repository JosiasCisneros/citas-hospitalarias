package com.CitasHospital.Citas.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Representa los bloques disponibles de un médico agrupados por fecha.
 */
public class HorariosMedicoDisponible {
    private Medico medico;
    private Map<LocalDate, List<EspacioCitaDisponible>> espaciosPorFecha;

    /**
     * Constructor de HorariosMedicoDisponible.
     */
    public HorariosMedicoDisponible(Medico medico,
            Map<LocalDate, List<EspacioCitaDisponible>> espaciosPorFecha) {
        this.medico = medico;
        this.espaciosPorFecha = espaciosPorFecha;
    }

    /**
     * Obtiene el médico asociado al grupo de horarios.
     */
    public Medico getMedico() {
        return medico;
    }

    /**
     * Establece el médico asociado al grupo de horarios.
     */
    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    /**
     * Obtiene los bloques disponibles agrupados por fecha.
     */
    public Map<LocalDate, List<EspacioCitaDisponible>> getEspaciosPorFecha() {
        return espaciosPorFecha;
    }

    /**
     * Establece los bloques disponibles agrupados por fecha.
     */
    public void setEspaciosPorFecha(Map<LocalDate, List<EspacioCitaDisponible>> espaciosPorFecha) {
        this.espaciosPorFecha = espaciosPorFecha;
    }
}
