# Recuperación de contraseña local

Esta demo utiliza Mailpit para capturar correo sin enviarlo a Internet. Requiere JDK 26, PowerShell 7 y el JAR compilado según el README.

## Iniciar

Desde la raíz del proyecto, con JAVA_HOME configurado:

```powershell
.\scripts\buzon-local.ps1 -Smtp 1025 -Web 8025
.\scripts\ejecutar-demo-local.ps1 -Puerto 18086 -RecuperacionLocal -Smtp 1025
```

Detener primero cualquier instancia anterior que use ese puerto o carpeta de datos. El lanzador conserva la base persistente y habilita el canal SMTP local.

- Aplicación: http://127.0.0.1:18086/login
- Buzón: http://127.0.0.1:8025

Registrar una cuenta ficticia con correo terminado en `@example.test`. Solicitar recuperación, abrir el mensaje en el buzón y seguir su enlace para elegir otra contraseña. Iniciar sesión con la nueva contraseña. No publicar mensajes, enlaces, tokens ni contraseñas.

El enlace requiere JavaScript: el token viaja en el fragmento, se copia al formulario POST y se retira de la dirección visible. Tras un error de validación, volver a abrir el enlace original del correo. Los tokens vencen, se almacenan como hash y solo pueden consumirse una vez. El restablecimiento invalida las sesiones anteriores en su siguiente petición autenticada.

## Detener

Ctrl+C detiene la aplicación. Desde otra terminal también puede usarse:

```powershell
.\scripts\detener-demo-local.ps1 -Puerto 18086
.\scripts\buzon-local.ps1 -Accion detener
```

El buzón utiliza [Mailpit 1.33.1](https://github.com/axllent/mailpit/releases/tag/v1.33.1), con licencia MIT. El script descarga el binario oficial de Windows amd64, verifica los hashes y conserva su licencia en `Citas/target/mailpit-02A`. Los mensajes y binarios quedan excluidos de Git. SMTP y web escuchan únicamente en loopback; no hay relay ni entrega externa.

Sin el perfil `recuperacion-local`, la demo no tiene un canal de envío de correo habilitado. El lanzador antiguo `ejecutar-recuperacion-local.ps1` se conserva como herramienta de diagnóstico efímera; para una demostración persistente usar los comandos de esta guía.
