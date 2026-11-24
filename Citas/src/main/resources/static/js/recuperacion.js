"use strict";
// El fragmento solo se lee en el navegador; nunca se envía en GET/Referer.
const token = new URLSearchParams(window.location.hash.slice(1)).get("token");
window.history.replaceState(null, "", window.location.pathname);
if (token && /^[A-Za-z0-9_-]{43}$/.test(token)) {
    document.getElementById("token-recuperacion").value = token;
    document.getElementById("restablecer").disabled = false;
    document.getElementById("estado-enlace").textContent = "Introduce tu nueva contraseña. El enlace solo puede usarse una vez.";
}
