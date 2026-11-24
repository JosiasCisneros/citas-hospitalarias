# Sesiones e identidad

Política confirmada por el usuario, aplicada tanto al perfil propio como a edición administrativa:

| Operación confirmada | Sesiones de la cuenta | Tokens pendientes |
|---|---|---|
| Correo efectivamente distinto | Requieren nuevo login | Anulados |
| Contraseña nueva no blanca | Requieren nuevo login | Anulados |
| Desactivación | Rechazadas; no se permite nuevo login | Anulados |
| Solo nombre, identificación o contacto | Se conservan | Se conservan |
| Mismo correo y contraseña omitida/blanca | Se conservan | Se conservan |
| Validación rechazada o transacción revertida | Se conservan | Se conservan |

Enviar otra vez la misma contraseña no blanca se trata como operación de credenciales y también revoca. No se cambia la política de complejidad existente.

Para cambios sensibles propios se cierra la sesión actual tras confirmar y se redirige al login con mensaje genérico; las demás sesiones se rechazan al siguiente acceso. Editar/desactivar a otra persona no cierra la sesión del administrador. Si el administrador se cambia/desactiva a sí mismo, se aplica la misma política de su cuenta.

Se conserva el principal con id y el sello del flujo de recuperación. El nombre histórico `versionRecuperacion` se mantiene para no cambiar esquema; ahora representa la versión de autenticación de la cuenta. La proyección del filtro exige también que la cuenta esté activa. No hay JWT, nuevo almacén ni servicio de sesiones.

Edición/desactivación toman el bloqueo de usuario de recuperación y refrescan su estado JPA antes de guardar. Datos, anulación de tokens y aumento de versión se confirman en la misma transacción. Los tokens pendientes se hacen inutilizables usando su marca existente `consumidoEn` como fecha de anulación; no se borra la fila ni se añade una columna. La decisión de cierre propio procede del cambio efectivo calculado por el servicio bajo bloqueo.

El perfil se identifica mediante el id del principal real; no se vuelve a buscar por el correo anterior después de editarlo. Se conserva compatibilidad con identidades genéricas de Spring Security ya autenticadas mediante resolución en servidor, nunca con el id aportado por el formulario.

La revocación al siguiente acceso es suficiente en esta instancia local: no interrumpe peticiones ya iniciadas ni acredita todas las carreras de citas, edición o disponibilidad. La integridad de agenda y la persistencia se describen en sus guías respectivas.

Verificar desde la raíz con `scripts/verificar.ps1 -JavaHome <ruta-JDK-26>`. La suite usa logins reales, repositorios y H2 aislada, sin SMTP ni credenciales manuales. La guía de arranque y detención continúa en CONFIGURACION_LOCAL.md.
