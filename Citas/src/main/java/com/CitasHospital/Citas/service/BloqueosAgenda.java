package com.CitasHospital.Citas.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import com.CitasHospital.Citas.model.*;
import com.CitasHospital.Citas.repository.*;

/** Orden de recursos: usuario -> médico -> disponibilidad -> cita.
 * La fila del médico protege su agenda incluso cuando la disponibilidad aún no existe. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class BloqueosAgenda {
    private final UsuarioRepository usuarios;
    private final MedicoRepository medicos;
    private final DisponibilidadMedicoRepository disponibilidades;
    private final CitaRepository citas;
    @PersistenceContext private EntityManager em;
    public BloqueosAgenda(UsuarioRepository usuarios, MedicoRepository medicos,
            DisponibilidadMedicoRepository disponibilidades, CitaRepository citas) {
        this.usuarios=usuarios;this.medicos=medicos;this.disponibilidades=disponibilidades;this.citas=citas;
    }
    public Usuario usuario(String correo) {
        var u=usuarios.buscarPorCorreoConBloqueo(correo)
                .orElseThrow(()->new IllegalArgumentException("No se encontro el usuario."));
        em.refresh(u);
        return u;
    }
    public Medico medico(Long id) {
        var m=medicos.buscarPorIdConBloqueo(id)
                .orElseThrow(()->new IllegalArgumentException("No se encontro el medico."));
        em.refresh(m);
        return m;
    }
    public DisponibilidadMedico disponibilidad(Long id) {
        Long medicoId=disponibilidades.medicoId(id)
                .orElseThrow(()->new IllegalArgumentException("La disponibilidad ya no esta disponible."));
        medico(medicoId);
        return disponibilidadBloqueada(id,medicoId);
    }
    private DisponibilidadMedico disponibilidadBloqueada(Long id,Long medicoId) {
        var d=disponibilidades.buscarPorIdConBloqueo(id)
                .orElseThrow(()->new IllegalArgumentException("La disponibilidad ya no esta disponible."));
        em.refresh(d);
        if(!d.getMedico().getId().equals(medicoId)) throw new IllegalArgumentException("La disponibilidad cambio; vuelve a consultarla.");
        return d;
    }
    public Cita cita(Long id) {
        var ref=citas.referencias(id).orElseThrow(()->new IllegalArgumentException("No se encontro la cita."));
        var u=usuarios.buscarPorIdConBloqueo(ref.getUsuarioId())
                .orElseThrow(()->new IllegalArgumentException("No se encontro el usuario."));
        em.refresh(u);
        medico(ref.getMedicoId());
        disponibilidadBloqueada(ref.getDisponibilidadId(),ref.getMedicoId());
        var c=citas.buscarPorIdConBloqueo(id).orElseThrow(()->new IllegalArgumentException("No se encontro la cita."));
        em.refresh(c);
        return c;
    }
}
