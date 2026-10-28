# ADR 0015 — Cuentas y accesos sin identificadores manuales

- Estado: aceptado para implementación local por el pedido del usuario.
- Fecha: 13 de octubre de 2026.
- Historias/tareas: HU-01, HU-04, HU-11; BK-005, BK-006, BK-010.

## Contexto y decisión

El usuario solicita sustituir la copia manual de UUID por búsqueda, selección, confirmación e invitación desde la ficha. El API conserva UUID internos. No se cambia el IdP, el stack, el almacenamiento ni se publica el sitio.

Se agrega un directorio de cuentas **registradas en ATO**, exclusivo de GERENTE, con búsqueda acotada por nombre/correo, rol, estado y paginación. Los nombres provienen del perfil OIDC verificado al iniciar sesión; un perfil sin nombre se presenta por correo. V9 agrega `display_name` de forma expansiva, sin reinterpretar las identidades/membresías anteriores. Se mantienen las relaciones de cartera y obra separadas.

La búsqueda usa POST con JSON y protección CSRF de sesión para evitar nombres/correos en URLs y logs de acceso. Se audita la lectura con rol/paginación, sin guardar consulta ni identidad personal en metadata; respuestas `no-store`.

Las fichas listan cuentas vinculadas con identidad legible, capacidad, estado y confirmación explícita de concesión/revocación. Las candidatas de una obra cliente/proveedor se filtran por la cartera correspondiente, además del rol activo. Seleccionar no concede acceso: el comando existente revalida permisos, cuenta/rol, versión, contraseña reciente y auditoría. No se vincula automáticamente por coincidencia de nombre o correo.

Las invitaciones siguen siendo de un uso, acotadas al correo verificado y al rol autorizado por gerencia. Se crea un enlace de aceptación con ID/token en **fragmento**, nunca en query ni logs del servidor. El navegador conserva la invitación temporalmente en sessionStorage para sobrevivir al login y la elimina al aceptar/cancelar/caducar. La usuaria abre el enlace y confirma; no introduce IDs ni códigos. El API anterior se conserva. Crear invitación no crea una identidad en el proveedor ni afirma envío SMTP; se comparte el enlace hasta configurar el correo real y el alta del IdP productivo.

## Límites y validación

El directorio no consulta Keycloak Admin ni enumera identidades externas. Personas sin rol aceptado y cuentas desactivadas no aparecen como candidatas para una función que exige ese rol; las vinculadas desactivadas siguen visibles para poder revocar. Nombre/correo permanecen privados, fuera de Git, fixtures reales y auditoría de payloads. Respuestas sin caché; asistente, cliente, proveedor y ADMIN no obtienen directorio global.

Validar migración/restore de V9, autorización entre identidades/recursos, filtros adversos y cambios de rol/estado después de seleccionar. UI: búsqueda, confirmación, revocación, invitación/aceptación, errores recuperables, teclado y móvil. Entrega/evidencia en [23](../23-cuentas-y-accesos.md). No se agregan dependencias.
