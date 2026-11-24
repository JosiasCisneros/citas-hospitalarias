package com.CitasHospital.Citas.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.CitasHospital.Citas.model.Medico;
import com.CitasHospital.Citas.repository.MedicoRepository;

/**
 * Contiene la lógica de negocio relacionada con la gestión de médicos.
 */
@Service
public class MedicoService {
    private final MedicoRepository medicoRepository;
    private final BloqueosAgenda bloqueos;

    /**
     * Constructor de MedicoService.
     */
    public MedicoService(MedicoRepository medicoRepository, BloqueosAgenda bloqueos) {
        this.medicoRepository = medicoRepository;
        this.bloqueos = bloqueos;
    }

    /**
     * Lista todos los médicos registrados.
     */
    public List<Medico> listarMedicos() {
        return medicoRepository.findAll();
    }

    /**
     * Lista los médicos activos.
     */
    public List<Medico> listarMedicosActivos() {
        return medicoRepository.findByActivoTrue();
    }

    /**
     * Lista las especialidades registradas sin repetir valores.
     */
    public List<String> listarEspecialidades() {
        return listarMedicos()
                .stream()
                .map(Medico::getEspecialidad)
                .filter(especialidad -> especialidad != null && !especialidad.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
    }

    /**
     * Obtiene un médico por su identificador.
     */
    public Medico buscarPorId(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el medico."));
    }

    /**
     * Crea un nuevo médico después de validar sus datos.
     */
    public Medico guardarMedico(Medico medico) {
        validarMedico(medico);
        Medico nuevo = new Medico();
        nuevo.setNombre(medico.getNombre());
        nuevo.setEspecialidad(medico.getEspecialidad());
        nuevo.setActivo(true);
        return medicoRepository.save(nuevo);
    }

    /**
     * Actualiza los datos de un médico después de validar sus datos.
     */
    @Transactional
    public Medico actualizarMedico(Long id, Medico datosMedico) {
        validarMedico(datosMedico);

        Medico medico = bloqueos.medico(id);
        medico.setNombre(datosMedico.getNombre());
        medico.setEspecialidad(datosMedico.getEspecialidad());

        return medicoRepository.save(medico);
    }

    /**
     * Desactiva un médico sin eliminar su información del sistema.
     */
    @Transactional
    public void desactivarMedico(Long id) {
        Medico medico = bloqueos.medico(id);
        medico.setActivo(false);
        medicoRepository.save(medico);
    }

    /**
     * Valida que los datos básicos del médico estén presentes.
     */
    private void validarMedico(Medico medico) {
        if (medico == null) {
            throw new IllegalArgumentException("Los datos del medico son obligatorios.");
        }

        if (medico.getNombre() == null || medico.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del medico es obligatorio.");
        }

        if (medico.getEspecialidad() == null || medico.getEspecialidad().isBlank()) {
            throw new IllegalArgumentException("La especialidad es obligatoria.");
        }
    }
}
