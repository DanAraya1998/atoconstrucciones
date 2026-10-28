# ADR 0016 — Registro con revisión y alta asistida de clientes

- Estado: aceptado por el usuario el 26 de octubre de 2026; implementación y evidencia en [24](../24-alta-clientes-y-obras.md).
- Historias/tareas: HU-00, HU-01, HU-11; BK-045. Amplía ADR 0015 sin retirar sus API.

## Decisión

Dos entradas comparten el mismo caso de uso: una persona se registra mediante OIDC y solicita acceso; gerencia registra un cliente/obra desde un contacto recibido o directamente. Gerencia puede aprobar la solicitud para una obra existente o crear cliente y obra en borrador en una operación. La representación contractual se elige explícitamente. Una solicitud pública de reunión no exige cuenta ni concede acceso.

El módulo `onboarding` coordina los servicios de identidad, cartera, obras y contactos. La decisión, relaciones, auditoría e idempotencia se guardan en PostgreSQL en una transacción. La creación de identidad y el correo se ejecutan después mediante una cola durable con lease y reintentos: una caída externa no deshace ni duplica la obra. Ningún controlador consulta repositorios de otro módulo.

Keycloak conserva contraseñas, registro y verificación del correo. Un adaptador de administración usa credenciales de servicio separadas; no recibe ni entrega contraseñas de las personas, no modifica cuentas desactivadas, credenciales ni roles del IdP de cuentas existentes. El correo de activación lo envía el IdP mediante su SMTP configurado. Sin integración configurada, la cola permanece pendiente y la interfaz lo indica; no se afirma envío.

La autorización aprobada se liga a issuer/subject, no solo al correo de contacto. Para altas asistidas el adaptador verifica la identidad exacta en el IdP y guarda ese vínculo antes de notificar. Solo una identidad habilitada con correo verificado coincidente puede completar la activación. Una cuenta existente se reutiliza; una ambigüedad no se resuelve automáticamente. No se reactivan cuentas suspendidas. Autorregistro no concede roles ni acceso a obras.

Los servicios de cartera y obras reciben la aprobación interna persistida para completar sus membresías; esa ejecución diferida no fabrica una autenticación reciente de gerencia. La aprobación original sí requiere contraseña reciente. La activación se ejecuta una vez y revalida estado de cuenta, cliente y obra. Revocar/cancelar antes de activar impide conceder acceso; quitar una membresía después no se deshace en el próximo login.

## Consecuencias y validación

V10 es expansiva y conserva cuentas, roles, invitaciones, contactos, contratos y membresías previos. Las vistas privadas y directorios nuevos son de GERENTE; cada solicitante solo consulta su solicitud y las obras ya autorizadas. El destino tras activar/login se decide en el servidor y no acepta redirecciones arbitrarias.

Validar autorización entre identidades y obras, aprobación/rechazo/revocación, representación, idempotencia/concurrencia, rollback de auditoría, indisponibilidad/reintentos del IdP/correo, actualización V9→V10 y restauración. UI: registro/solicitud, conversión de contacto, alta asistida, aprobación para obra existente, activación, móvil y teclado. El alta no activa el contrato ni la obra.

Fuentes técnicas: [registro OIDC con prompt=create](https://www.keycloak.org/docs/latest/server_admin/index.html), [API administrativa de Keycloak](https://www.keycloak.org/docs-api/latest/rest-api/index.html). No se cambia el stack ni se agregan dependencias.
