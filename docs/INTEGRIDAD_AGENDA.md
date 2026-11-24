# Integridad de agenda

## Reglas confirmadas

- Se reservan bloques de exactamente 30 minutos, con inicio y límites de disponibilidad sin segundos ni nanosegundos. La cuadrícula es relativa al comienzo: por ejemplo, una disponibilidad a las 09:10 admite 09:10 y 09:40. No se obliga a :00/:30.
- Fecha y hora de inicio deben ser futuras. No se admiten bloques fuera de límites ni turnos que crucen medianoche. Dos intervalos contiguos no se solapan.
- No se confirma una cita cuyo inicio ya ocurrió o coincide con ahora. Se conserva PENDIENTE → CONFIRMADA, PENDIENTE/CONFIRMADA → CANCELADA y la imposibilidad de reactivar CANCELADA.
- El paciente cancela únicamente antes del inicio; el administrador conserva la cancelación sin ese límite.
- Una disponibilidad con cualquier cita asociada, incluso cancelada, no se edita ni elimina. Cancelar libera el bloque para otra reserva futura, preservando el historial.
- Desactivar usuario/médico impide reservas posteriores a esa confirmación y conserva las citas existentes. Si una reserva confirma primero, la desactivación posterior la conserva. No hay cancelación automática.

## Tiempo

`TiempoAgenda` recibe el único Clock de producción y lo interpreta en `America/Costa_Rica`. No cambia el bean existente ni la comparación por Instant de recuperación. La suite usa un Instant fijo para probar pasado, inicio exacto, futuro y zona sin esperar tiempo real.

## Bloqueos de escritura

Orden de recursos: **usuario → médico → disponibilidad → cita**. Los métodos del coordinador requieren una transacción activa. Cada entidad se refresca después de adquirir el bloqueo para no tomar decisiones con estado JPA antiguo.

| Operación | Recursos bloqueados |
|---|---|
| Reservar | Usuario del paciente, médico de la agenda, disponibilidad |
| Confirmar/cancelar cita | Usuario dueño, médico, disponibilidad, cita |
| Crear disponibilidad | Médico; protege también la ausencia de una nueva fila |
| Editar/eliminar disponibilidad | Médico, disponibilidad |
| Editar/desactivar médico | Médico |
| Editar/desactivar usuario y recuperación | Usuario, según el protocolo de sesiones y recuperación |

No hay un lock global ni un lock Java como garantía de integridad. La fila estable del médico serializa su agenda; la del paciente impide cruces entre médicos. Las proyecciones de ids permiten conocer los padres sin bloquear hijos antes. Los vínculos de una cita y el médico de una disponibilidad no se cambian desde los flujos entregados.

Los cruces y ocupaciones se consultan de nuevo por SQL bajo bloqueo. El cruce de disponibilidades usa un resultado escalar para no validar horarios desde entidades cacheadas; la ocupación del médico incluye todas sus disponibilidades de esa fecha, incluso datos heredados solapados. Las vistas de bloques usan esa misma ocupación por médico. El historial y las claves foráneas se conservan.

Los conflictos de negocio se presentan mediante mensajes de los controladores; los timeouts de bloqueo conocidos tienen un consejo MVC limitado a esos errores y flujos. No se convierten todas las excepciones o errores de integridad en conflictos. Una edición contra una disponibilidad ya eliminada vuelve al listado sin intentar recargar la fila inexistente.

## Evidencia y límites

AgendaIntegrationTests contiene casos sobre H2 con aislamiento READ_COMMITTED, incluidas carreras con dos conexiones/transacciones y barrera antes de los locks. Se verifica el estado persistido; las barreras no se sitúan dentro de secciones mutuamente excluyentes. La prueba separada de timeout retiene deliberadamente una fila con un latch y comprueba la respuesta MVC al agotarse los 3000 ms configurados en la BD de prueba; libera al titular en finally.

Los intercalados de médico/disponibilidad preleídos usan dos transacciones secuenciales para comprobar estado JPA obsoleto; no se presentan como paralelismo. Las pruebas secuenciales cubren además los dos órdenes válidos de desactivación/reserva de usuario y médico. Confirmar y luego cancelar puede aceptar ambas acciones; no se impone un ganador artificial en esa carrera.

Se protegen las operaciones que siguen este protocolo en la aplicación. No es una prueba de carga, una garantía frente a escrituras SQL ajenas al protocolo ni evidencia de otro motor/aislamiento. Los timeouts normales siguen la configuración de BD; el valor instrumentado aquí es el de la suite de prueba. El perfil persistente usa el esquema versionado con Flyway.

Referencias primarias del mecanismo: [H2, transacciones y aislamiento](https://h2database.com/html/advanced.html), [H2, LOCK_TIMEOUT y comandos](https://h2database.com/html/commands.html), [Jakarta EntityManager.refresh](https://jakarta.ee/specifications/platform/11/apidocs/jakarta/persistence/EntityManager.html). La garantía concreta se sustenta en las pruebas del proyecto, no solo en estas referencias.
