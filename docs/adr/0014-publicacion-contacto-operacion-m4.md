# ADR 0014 — Sitio, publicación, contacto y operación M4

- Estado: aceptado para implementación local; exposición real sujeta a las puertas de release.
- Fecha: 9 de octubre de 2026.
- Relacionados: [ADR 0011](0011-google-drive-evidencias.md), [ADR 0006](0006-oidc-y-sesiones.md), [M4](../21-entrega-m4.md).

## Contexto

El usuario autorizó M4 y eligió `atoconstrucciones.com`, hosting local con Cloudflare Tunnel y Gmail SMTP. Aceptó RPO ≤24 h / RTO ≤4 h. Definió la responsable del aviso en la sesión; su identidad se configura en el entorno privado, sin datos personales reales en Git. El correo del aviso todavía no existe. Estas decisiones no autorizan desplegar una release incompleta ni inventar servicios, derechos fotográficos o remitentes.

## Decisión

Conservar Java 27/Boot 4.2.0-M2, Next y Drive. Las páginas públicas consultan DTO explícitos bajo `/api/v1/public`; el resto de los endpoints mantiene autenticación/RBAC. Textos comerciales y fichas tienen borrador/aprobación independientes. GERENTE aprueba con contraseña reciente, idempotencia, ETag y auditoría transaccional; OPERATIONS prepara fichas de obras asignadas. Portafolio independiente no crea obra financiera: reutiliza la custodia privada de una fotografía/documento con consentimiento, sin asociar la ficha a una obra operativa.

Consentimientos web usan códigos exactos `PORTFOLIO` / `WEBSITE`. Los anteriores de texto libre se conservan y requieren registrar alcance web explícito. Cada imagen exige consentimiento vigente de esa foto y evidencia limpia. La revisión editorial declara que personas, placas, domicilio y otros datos visibles tienen tratamiento adecuado; no se presume detección automática de rostros. Se generan JPEG de hasta 1600 px en JVM hijo sin credenciales, heap/deadline acotados, desde píxeles nuevos sin EXIF/GPS/comentarios. Originales y variantes permanecen privados en Drive; API sirve únicamente variantes autorizadas, con `no-store`. Next permite optimización únicamente del logo local, cerrando la ruta alternativa `/_next/image` que podría conservar una variante retirada. CDN/proxy deben respetar y excluir estas rutas de caché.

V8 agrega publicación/historia/variantes, contenido del sitio, contactos, leases y delegación. No modifica V1–V7. La retirada y caducidad de consentimiento se comprueban por petición, sin depender de un job ni de purgar un CDN. Los bytes obtenidos anteriormente por terceros no pueden recuperarse; sí se impide nueva distribución controlada. El cierre de obra no publica automáticamente.

El contacto anónimo exige Origin exacto, JSON ≤16 KiB, aviso vigente aceptado, UUID de reintento, honeypot y cuota durable por fuente (HMAC, sin IP en claro). El origen loopback del túnel puede confiar en `CF-Connecting-IP` solo con configuración explícita y acceso local exclusivo; no interpreta `X-Forwarded-For`. Contacto y notificación pendiente se guardan juntos antes de SMTP. `outbox_job` de M0 tiene FKs específicas del recurso/actor sintético; por eso el contacto usa una cola tipada `contact_delivery` con el mismo mecanismo lease/backoff/DEAD, sin falsificar una cuenta visitante ni un recurso M0. SMTP no garantiza exactly-once: Message-ID estable correlaciona; caída tras aceptación remota puede duplicar notificación. Correo mínimo sin nombre, email ni mensaje del visitante, sin reply-to derivado de entrada. Asistente requiere delegación explícita para consultar la bandeja global.

Cloudflare Tunnel apunta a Next en loopback y al IdP bajo `/idp`; DB, Actuator y scanner no se exponen. La plantilla no crea ni inicia un túnel. Gmail usa STARTTLS obligatorio y credencial externa; envío desactivado hasta configurar y confirmar cuenta/remitente. Las exigencias de la cuenta Gmail son independientes de la política MFA de ATO, que sigue opcional/desactivada.

Se prepara CLI de respaldo de DB/revisiones Drive con SHA-256, cifrado AES-256-GCM, archivos creados sin sobrescribir y clave externa mode 600. Exportar exige declarar escrituras/integraciones pausadas. El ensayo local usa DB, objetos y clave sintéticos; el procedimiento real exige además escrow del IdP/credenciales, prueba de permisos Drive y mapeo autorizado si cambian IDs/revisiones. El CLI inicial limita cada archivo a 256 MiB; bases mayores requieren ampliar el formato y ensayarlo antes de usarlo. No es un sistema de PITR ni un SLA demostrado.

## Consecuencias y validación

No se introduce proveedor de almacenamiento ni dependencia backend. Se añaden herramientas de test `@axe-core/playwright` 4.13.0 (MPL-2.0) y Lighthouse 13.5.0 (Apache-2.0), fijadas en lockfile; fuentes/licencias/mantenimiento revisados: [Axe oficial](https://github.com/dequelabs/axe-core-npm/tree/develop/packages/playwright), [seguridad Axe](https://github.com/dequelabs/axe-core-npm/security), [Lighthouse oficial](https://github.com/GoogleChrome/lighthouse), [seguridad Lighthouse](https://github.com/GoogleChrome/lighthouse/security). No se añaden al runtime. Auditoría npm de esta entrega y resultados ejecutados en [21](../21-entrega-m4.md).

Fuentes operativas revisadas: [configuración Tunnel](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/local-management/configuration-file/), [parámetros del origen](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/origin-parameters/), [SMTP Gmail](https://support.google.com/mail/answer/7104828), [contraseñas de aplicación](https://support.google.com/accounts/answer/185833). Son capacidades del proveedor, no evidencia de configuración ejecutada en la cuenta del usuario.
