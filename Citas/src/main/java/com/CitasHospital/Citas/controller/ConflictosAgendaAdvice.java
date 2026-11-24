package com.CitasHospital.Citas.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Solo conflictos de bloqueo conocidos de estos flujos; no captura errores inesperados. */
@ControllerAdvice(assignableTypes={CitaController.class,DisponibilidadController.class,MedicoController.class,UsuarioController.class})
public class ConflictosAgendaAdvice {
    @ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.QueryTimeoutException.class,jakarta.persistence.LockTimeoutException.class,
            jakarta.persistence.PessimisticLockException.class,jakarta.persistence.QueryTimeoutException.class})
    public String bloqueo(HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error","Otro proceso esta modificando estos datos. Vuelve a intentarlo.");
        String ruta=request.getRequestURI().substring(request.getContextPath().length());
        String destino;
        if(ruta.startsWith("/admin/citas")) destino="/admin/citas";
        else if(ruta.startsWith("/citas/cancelar")) destino="/citas/mis-citas";
        else if(ruta.startsWith("/citas")) destino="/citas/disponibles";
        else if(ruta.startsWith("/disponibilidades")) destino="/disponibilidades";
        else if(ruta.startsWith("/medicos")) destino="/medicos";
        else if(ruta.startsWith("/usuarios")) destino="/usuarios";
        else destino="/perfil";
        return "redirect:"+destino;
    }
}
