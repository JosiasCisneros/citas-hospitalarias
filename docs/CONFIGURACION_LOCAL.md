# Configuración y persistencia local

Demo académica de una sola instancia: MVC/Thymeleaf, H2 2.4.240 en archivo y Flyway. La consola H2 sigue deshabilitada y el servidor escucha en loopback. No representa operación hospitalaria real ni una instalación pública validada.

## Compilar y comprobar

Desde la raíz que contiene `Citas/pom.xml`, con JDK 26 y PowerShell 7 ya instalado:

```powershell
.\scripts\verificar.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.1'
```

El wrapper informa el JDK/Maven efectivo, ejecuta las regresiones y genera `Citas/target/Citas-0.0.1-SNAPSHOT.jar`. Las pruebas usan bases exclusivas en memoria; agenda ejecuta V1 con Flyway y Hibernate validate. El test de reinicio crea además una carpeta temporal exclusiva y dos JVM sucesivas; no abre la demo del usuario.

## Demo persistente

```powershell
.\scripts\ejecutar-demo-local.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.1'
```

Activa `persistencia-local,demo-local`, solicita la contraseña sin mostrarla y arranca en `http://127.0.0.1:18083/login`. Para una base sin inicialización administrativa:

```powershell
.\scripts\ejecutar-demo-local.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.1' -SinAdministrador
```

El administrador es optativo; `demo-local` valida antes de escribir, no eleva pacientes y no modifica una cuenta administradora existente, incluso si se suministra otra contraseña al reiniciar. Sin ese perfil no se ejecuta el inicializador. No es un mecanismo para restablecer contraseñas.

El archivo por defecto es **`<raíz-del-proyecto>/datos-locales/demo/hospital.mv.db`**, fuera de target. El script muestra la ruta absoluta al comenzar. No depende de la carpeta de la terminal. Para elegir otra ubicación:

```powershell
.\scripts\ejecutar-demo-local.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.1' -DirectorioDatos 'D:\DatosCitas\demo' -Puerto 18084
```

También admite `CITAS_DATOS`. Precedencia: parámetro `-DirectorioDatos`, variable externa y, finalmente, carpeta por defecto. Una ruta relativa se resuelve **desde la raíz del proyecto**, nunca desde el directorio actual. Se rechazan punto y coma y rutas bajo Citas/target. No ejecutar dos instancias sobre la misma carpeta: no se habilita AUTO_SERVER. Las carpetas de datos/archivos H2 están excluidos de versionado; al elegir otra ubicación, tampoco versionar sus copias o datos.

Identidad sintética del administrador, si no se configura otra: `admin-demo@example.test`, `Administrador ficticio`, identificación `DEMO-ADMIN`, contacto ficticio. Se pueden suministrar `DEMO_ADMIN_NOMBRE`, `DEMO_ADMIN_CEDULA`, `DEMO_ADMIN_CORREO` y `DEMO_ADMIN_PASSWORD` externamente. El correo debe terminar en `@example.test`; contraseña de alta de al menos 12 caracteres y máximo 72 bytes UTF-8, sin respaldo fijo. No incluir secretos en comandos, archivos versionados o capturas; el lanzador usa el entorno del hijo para la contraseña, no sus argumentos. El valor suministrado sigue validándose aunque la cuenta ya exista.

## Cierre y copia de respaldo

Ctrl+C en la terminal del lanzador termina únicamente la JVM creada por ese lanzador. Desde otra terminal:

```powershell
.\scripts\detener-demo-local.ps1 -Puerto 18083
```

La parada valida PID, instante de inicio, ejecutable, JAR y puerto contra su registro local antes de terminar esa JVM. No detiene procesos por nombre ni borra la base. El registro de proceso se guarda en target/demo-local y el lanzador lo retira al finalizar. Es una terminación del proceso, no un endpoint de apagado ni un servicio de Windows. Esperar a que el lanzador haya terminado antes de copiar o volver a abrir los datos.

Con la aplicación **detenida**, copiar la carpeta de datos completa a otra ubicación privada. Ejemplo, escogiendo un destino nuevo:

```powershell
Copy-Item -LiteralPath '.\datos-locales\demo' -Destination 'D:\RespaldosCitas\demo-2026-11-14' -Recurse
```

No copiar el archivo mientras la JVM lo tiene abierto. Para usar una copia, mantener el original intacto y arrancar con `-DirectorioDatos` apuntando a la carpeta copiada. No se incluye automatización de backups ni un comando de borrado. Maven clean puede borrar target y los registros, pero no la carpeta de datos por defecto. No ejecutar clean mientras la demo está activa.

## Esquema y una base incompatible

`V1__esquema_inicial.sql` crea el esquema actual, con claves foráneas, unicidad, enums, precisión temporal y sello de autenticación. Flyway registra la versión; Hibernate usa **validate**, sin crear/actualizar/borrar tablas en el perfil persistente. V1 no contiene datos de demo. Reiniciar valida el historial y conserva los datos; no se inventa una V2 sin un cambio real.

Una base previa sin historial Flyway o con esquema incompatible debe detener el arranque. No activar `baseline-on-migrate`, clean, ddl-auto=update/create/create-drop ni editar V1 para forzar su apertura. Detener la aplicación, conservar una copia y revisar la causa con el analista. Para demostrar una instalación nueva se puede elegir **otra carpeta vacía**, manteniendo la anterior intacta. No se ofrece conversión automática de bases académicas anteriores. Tampoco se cambian claves/contraseñas por reiniciar.

## Recuperación local combinada

```powershell
.\scripts\ejecutar-demo-local.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.1' -RecuperacionLocal -Smtp 1025
```

Añade `recuperacion-local` conservando la misma carpeta persistente. Requiere iniciar aparte el buzón loopback según [RECUPERACION_LOCAL.md](RECUPERACION_LOCAL.md). No versionar buzones, correos ni tokens.

## Arranque directo y modo efímero

Para usar el JAR directamente, definir `CITAS_DATOS` con una **ruta absoluta** y activar `persistencia-local` (y opcionalmente `demo-local,recuperacion-local`). El perfil requiere una carpeta absoluta sin punto y coma; no asume la carpeta de la terminal. Evitar overrides de spring.datasource, Flyway o ddl-auto: el lanzador es el recorrido recomendado y neutraliza el override heredado SPRING_DATASOURCE_URL.

El arranque normal **sin persistencia-local** conserva H2 en memoria y pierde datos al cerrar la JVM. Es deliberadamente distinto de la demo persistente. El lanzador antiguo `ejecutar-recuperacion-local.ps1` sigue siendo un entorno efímero aislado de diagnóstico, no la vía para conservar datos.

El escape CSV trata prefijos de fórmula como texto mediante apóstrofo y conserva comillas/Unicode/saltos; no garantiza el comportamiento de todos los importadores de hojas de cálculo.

Referencias técnicas: [inicialización y Flyway en Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html) y [baseline-on-migrate de Flyway](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting). Las versiones efectivas del proyecto se comprueban en el build, sin actualizar Spring/Java.

Nota de entorno: Windows PowerShell 5.1 de esta máquina rechaza scripts por su política; usar PowerShell 7 ya instalado. No se cambian políticas de Windows.
