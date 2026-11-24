"use strict";
// Mejora progresiva: el servidor sigue recibiendo el formulario POST original.
(() => {
    let enviados = new WeakSet();
    const formularios = [];
    const soportaDialogo = typeof HTMLDialogElement !== "undefined"
        && typeof HTMLDialogElement.prototype.showModal === "function"
        && typeof HTMLFormElement.prototype.requestSubmit === "function";

    function preparar(selector, id, cargarDatos) {
        const dialogo = document.getElementById(id);
        const formas = Array.from(document.querySelectorAll(selector));
        if (!soportaDialogo || !dialogo || !formas.length) return false;
        const volver = dialogo.querySelector("[data-volver]");
        let pendiente = null;
        let autorizado = null;
        let disparador = null;
        formas.forEach(form => {
            formularios.push(form);
            form.addEventListener("submit", event => {
                if (enviados.has(form)) { event.preventDefault(); return; }
                if (autorizado === form) {
                    autorizado = null;
                    enviados.add(form);
                    form.setAttribute("aria-busy", "true");
                    return;
                }
                event.preventDefault();
                if (dialogo.open) return;
                disparador = event.submitter || form.querySelector("button[type=submit]");
                pendiente = { form, submitter: disparador };
                cargarDatos(form);
                dialogo.showModal();
                volver.focus();
            });
        });
        volver.addEventListener("click", () => dialogo.close());
        // Escape conserva el comportamiento nativo. Siempre se restaura el disparador.
        dialogo.addEventListener("close", () => {
            pendiente = null;
            if (disparador && disparador.isConnected) disparador.focus();
        });
        dialogo.querySelector("[data-enviar]").addEventListener("click", () => {
            if (!pendiente || enviados.has(pendiente.form)) return;
            const { form, submitter } = pendiente;
            pendiente = null;
            autorizado = form;
            dialogo.close();
            try { form.requestSubmit(submitter); }
            finally { autorizado = null; }
        });
        return true;
    }
    const reserva = preparar("form[data-revisar-reserva]", "revisar-reserva", form => {
        for (const campo of ["medico", "especialidad", "fecha", "inicio", "fin"]) {
            document.getElementById("revision-" + campo).textContent = form.dataset[campo];
        }
    });
    const cancelacion = preparar("form[data-revisar-cancelacion]", "cancelar-cita", () => {});
    const accion = preparar("form[data-revisar-accion]", "confirmar-accion", form => {
        document.getElementById("titulo-accion").textContent = form.dataset.titulo;
        document.getElementById("mensaje-accion").textContent = form.dataset.mensaje;
        const enviar = document.getElementById("confirmar-accion").querySelector("[data-enviar]");
        enviar.textContent = form.dataset.accion;
        // Restablecer ambos estilos en cada apertura del diálogo compartido.
        enviar.classList.toggle("boton", form.dataset.estilo === "primario");
        enviar.classList.toggle("boton-peligro", form.dataset.estilo !== "primario");
    });
    if (reserva || cancelacion || accion) document.body.classList.add("dialogos-agenda");

    // Todo esta abierto en el HTML. Bootstrap mejora la agrupacion, sin ocultar sin JS.
    if (window.bootstrap && window.bootstrap.Collapse) {
        document.querySelectorAll("#accordionMedicos .accordion-collapse").forEach((panel, indice) => {
            if (indice > 0) window.bootstrap.Collapse.getOrCreateInstance(panel, { toggle: false }).hide();
        });
    }
    // Al volver desde la cache de navegacion se puede intentar otra operacion.
    window.addEventListener("pageshow", () => {
        enviados = new WeakSet();
        formularios.forEach(form => form.removeAttribute("aria-busy"));
    });
})();
