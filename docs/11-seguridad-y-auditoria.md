# 11 — Seguridad y auditoría

[Índice](README.md) · [Permisos](03-roles-y-permisos.md) · [Documentos](10-documentos-e-integraciones.md) · [Operación](14-despliegue-y-operacion.md)

## Activos y fronteras de confianza

Activos: identidad/sesiones; contratos y cobros/costos; ubicación/contactos; documentos/XML; información personal de gerente; tokens Google; evidencias de autorización; backups/claves y publicación. Fronteras: internet↔proxy; navegador/Flutter↔Spring; Spring↔IdP/DB/objetos; worker↔archivos/external APIs; operación técnica↔datos sensibles. Ninguna entrada del navegador, adjunto o proveedor se considera confiable por estar autenticada.

## Modelo de amenazas y controles mínimos

| Amenaza / ejemplo | Control y evidencia necesaria |
|---|---|
| IDOR/BOLA: cliente cambia UUID de obra, documento o extra | RN-A02, scoped queries, validación de todos los IDs, DTO mínimo; T-RBAC con cliente A/B, proveedor A/B y IDs conocidos |
| Escalada/mass assignment: body envía rol/estado/owner | DTO allowlist, estado por comando, pertenencia de servidor, privilegio GERENTE explícito; T-RBAC/T-API |
| Doble confirmación/reversión o pérdida de edición | Idempotencia, unicidad y locks/versionado, auditoría atómica; T-CONCUR/T-CASH |
| Robo de sesión/CSRF y recuperación abusiva | OIDC, cookies seguras, CSRF, reautenticación reciente, MFA opcional, sesiones revocables, recuperación de un uso y rate limit; T-AUTH |
| XSS en contacto/notas/XML o SSRF al renderizar PDF | Texto sanitizado/escapado, CSP, no HTML arbitrario, preview aislado, no fetch de URLs usuario desde worker; T-FILE/T-SEC |
| XXE, XML bomb o expansión de esquema | RN-D03, XSD local revisado, límites de recursos y pruebas de entidades/DTD; T-XML |
| Archivo malicioso y PDF/imagen de descompresión enorme | Cuarentena, scanner, MIME/decodificación limitados, procesos con límites y sin red innecesaria; T-FILE |
| Filtración por cache/log/export/job | `no-store` privado, claves por identidad, allowlist de logs, permisos al ejecutar/descargar export; T-PRIVACY/T-RBAC |
| ADMIN accede a finanzas personales con permiso técnico | Soporte sin payload; grants autorizados/restringidos, cuentas DB separadas, claves fuera de DB, auditoría externa; T-SUPPORT |
| Token Google expuesto o invitación duplicada | Cifrado, scopes mínimos, state, mappings/outbox y consentimiento explícito; T-CALENDAR |
| Foto publica domicilio/rostro sin autorización | Consentimiento por alcance, EXIF eliminado, revisión editorial y retirada; T-PUBLISH |
| Backups faltantes/inconsistentes o ransomware | Copias cifradas con versiones/inmutabilidad según proveedor, cuenta de backup separada, restore ensayado; T-RESTORE |
| Dependencia comprometida/configuración por defecto | Lockfiles/BOM, SBOM/scan, secretos fuera de Git, revisión de mantenimiento/licencia; T-STACK/T-DEPLOY |

## Identidad y sesiones

Keycloak local elegido para desarrollo (P-02 productivo pendiente), sin passwords propios en ATO. Invitación liga identidad verificada y capacidades mínimas; token corto de un uso con hash en servidor. Por decisión del usuario MFA queda desactivado. M0 exige reautenticación con contraseña ≤5 minutos para roles, invitaciones y soporte; MFA puede activarse mediante configuración y flujo del IdP. La política de exportación personal se concreta con su hito. No usar preguntas de seguridad ni recuperación que permita al técnico entrar como la gerente.

M0 implementa inactividad 30 minutos y reautenticación ≤5 minutos para acciones privilegiadas. Máximo absoluto 12 horas permanece propuesto, sin implementación en M0; concretar al preparar producción. Cookie `Secure`, `HttpOnly`, alcance limitado, `SameSite=Lax`; rotar identificador al login/privilegio y revocar logout/desactivación. Sesiones con DB o almacén mantenido si se escala; sin Redis obligatorio inicialmente. Revocación de permisos se verifica en backend sin esperar a que expire un token largo.

Bearer Flutter valida firma/issuer/audience/exp, algoritmo allowlist y JWKS controlado; no acepta token Google Calendar como token ATO. Authorization Code + PKCE, sin implicit/password grant. La seguridad OAuth propuesta toma como referencia [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html); scopes de API no sustituyen RBAC por recurso.

Rate limits propuestos por IP e identidad con respuesta 429/Retry-After: login/recuperación/invitación con backoff, contacto público 5/min y 20/h por IP con ajuste; uploads con cuota de bytes y concurrencia; export/matching con límite de jobs. No lockout permanente fácilmente provocable por terceros. Trust de `X-Forwarded-For` solo desde proxy conocido; política exacta medida, no hardcode global sin observar operación.

## Autorización, datos y secretos

Aplicar la [matriz completa](03-roles-y-permisos.md) en servicios de aplicación y consultas, no solo anotación del controller. DTO separados; en listas/exportaciones comprobar el mismo predicado de acceso. Validar obra/ámbito de cada documento, tarea, cotización e imputación; un UUID no es permiso. Rechazo por recurso ajeno no revela totales/IDs existentes.

TLS externo e interno cuando cruce red no confiable, cifrado en DB/objetos/backups y cifrado de tokens/cuentas sensibles a nivel de aplicación según amenaza. Claves en secret manager o variables inyectadas, separadas por entorno/ámbito, permisos mínimos y rotación ensayada; nunca en Markdown, `.env` versionado o argumentos de proceso que se loggeen. Variables públicas Next (`NEXT_PUBLIC_*`) solo config no secreta.

Finanzas personales: esquema/repositorio/DTO/search y documentos separados, titular GERENTE, sin agregación empresarial. Clave/rol DB separados propuestos si hosting lo permite. Esto limita errores y accesos corrientes; no constituye garantía criptográfica contra operador que controle runtime y claves. Soporte autorizado usa streaming por petición; [procedimiento excepcional](03-roles-y-permisos.md) no habilita approvals financieras ni impersonación.

Headers propuestos: CSP probada con Next/SVAR/Motion, `frame-ancestors`, `X-Content-Type-Options`, política de referrer, HSTS tras validar TLS y permisos mínimos del navegador. CSRF para sesión, CORS allowlist para clientes autorizados y validación Bean/domain. Sin stacktrace en Problem Details; Actuator restringido a operación, no internet abierto. Scanner propuesto como servicio separado: ClamAV declara GPLv2 y mantenimiento de firmas; verificar despliegue/licencia/imagen final antes de adoptar. [Documentación oficial ClamAV](https://docs.clamav.net/).

## Auditoría y logging

Evento crítico atómico: actor real, ámbito, recurso/versión, acción, instante UTC, razón/evidencia referenciada, traceId, grant de soporte si hubo, resultado y before/after mínimos protegidos. Operaciones críticas: confirmación/reversión de dinero, contratos/extras/cotizaciones, distribución de costos, conciliación, baseline/avance público, cerrar/reabrir, publicar/retirar, roles/consentimiento, soporte y exportaciones.

Tabla append-only con rol de escritura acotado; copia periódica en destino protegido externo a permisos ordinarios. No prometer inviolabilidad de auditoría en la misma DB ante superusuario: una cadena hash/sello puede facilitar detección, pero solo anclaje externo y separación operativa elevan resistencia. Cambios aplicados no se eliminan para corregir un error.

Logs técnicos JSON con traceId/operationId/resultado y duración; excluyen tokens, cookies, URL firmada, contenido XML, cuerpos financieros/personales, números completos de cuenta y adjuntos. Identificadores de actor/obra son sensibles y su lectura/retención también se limita. Fallo de persistencia de auditoría revierte mutación crítica; fallo de envío externo se reintenta desde outbox sin perder evento local.

## Normativa costarricense: evidencia y decisiones abiertas

Estas son implicaciones técnicas propuestas basadas en fuentes oficiales consultadas, no una certificación de cumplimiento de ATO. Separar alcance legal, política empresarial y controles implementados; P-10 exige revisar responsable, finalidad, destinatarios, retención y proveedores antes de producción.

| Fuente oficial consultada | Alcance comprobado / implicación del diseño | Pendiente específico |
|---|---|---|
| Ley 8968, copia institucional del Banco Nacional; art. 5 visible en búsqueda oficial | Información previa sobre tratamiento y consentimiento expreso cuando corresponda; diseñar aviso versionado y evidencia por finalidad, especialmente publicación | Aplicabilidad/excepciones, responsable y texto exacto revisados antes de recolectar en producción. [Ley 8968 institucional](https://www.bncr.fi.cr/hubfs/LEY%20No%208968.pdf?hsLang=es-cr) |
| PRODHAB, preguntas frecuentes; referencia a reglamento 37554-JP | Consentimiento específico/revocable; distinguir transferencia entre responsables de prestación por encargado; retención con excepciones. No fijar diez años universalmente para todo archivo | Inscripción/aplicabilidad de bases, contratos con encargados, acceso/rectificación y política por clase. [PRODHAB](https://www.prodhab.go.cr/preguntasfrecuentes.aspx) |
| Ley 8454 y reglamento oficial | Documentos/firma digital tienen marco jurídico específico; aceptación autenticada del portal no se afirma equivalente a firma digital certificada | P-07: forma de aprobación contractual; no emitir certificados ni validar firma legal completa por pasar XSD. [Reglamento oficial en SINALEVI](https://sinalevi.go.cr/ResultadosNormativa/Informacion?param1=56884&param2=145223&param3=1&param4=) |
| Hacienda: anexos 4.4 y transitorio oficial | Importación revisa esquemas/tipos y conserva original/mensajes, no prueba pago; emisión/recepción tributaria online fuera de alcance | Revisiones vigentes, tratamiento fiscal, plazos de conservación y receptor de documentos, BK-020/P-05. [Anexos oficiales](https://www.hacienda.go.cr/docs/ANEXOS_Y_ESTRUCTURAS_V4.4.pdf) |

Algunas páginas/PDF legales completos no pudieron descargarse por el navegador de investigación; las afirmaciones anteriores se limitan al contenido oficial legible de búsqueda y al reglamento/anexos accesibles. Antes de redactar políticas definitivas, BK-013/BK-020 deben obtener texto consolidado vigente; no tratar un enlace como revisión completa ya realizada.

Implementar mecanismo de solicitud de acceso/rectificación/revocación y retención por clase con holds por obligación legal; no “derecho al olvido” que borre movimientos aún sujetos a conservación. Aviso para contactos, usuarios y publicación; consentimiento fotográfico identifica material/finalidad/medio/plazo y quien lo otorga, con retiro documentado. Región/hosting no se fija como requisito legal por suposición; se decide con política/proveedor en P-10/P-11.

## Incidentes y verificaciones

Runbook: detectar/contener, revocar sesiones/grants/tokens, aislar archivos, preservar evidencia, investigar alcance, restaurar/corregir y documentar decisiones; las obligaciones de notificación se determinan según norma/política aplicable, sin inventar un plazo. Revisar amenazas al añadir banco, Calendar, nuevo parser o cliente Flutter. T-SEC/T-RBAC/T-SUPPORT/T-PRIVACY y ensayo de restore son gates, no lista decorativa.

## Controles y pendientes de privacidad M1

El usuario declaró que responsable/contacto y política definitiva aún no existen. Avisos y consentimiento se implementan como registros versionados con aprobación explícita, finalidad/material/medio/vigencia, evidencia y retiro auditable; no se precarga un aviso ficticio. Documentos contractuales/evidencias tienen holds sin plazos legales inventados. Solicitudes de acceso/rectificación/retiro se aíslan por identidad y se atienden por gerencia. Texto/legalidad/retención permanentes siguen pendientes antes de producción.

La consulta de texto completo de Ley 8968/PRODHAB en esta entrega encontró 502/403; SINALEVI accesible no entregó el cuerpo dinámico completo. Esta evidencia no completa la revisión jurídica de BK-013. No se declara cumplimiento legal. Ver [límites M1](17-entrega-m1.md).

## Google Drive, actualización del almacenamiento

La carpeta y sus permisos heredados son una frontera adicional. ATO no crea enlaces públicos ni permisos anyone/domain; el adaptador rechaza exposición pública detectada y revalida acceso/hash/revisión al descargar. Solo custodios acordados deben tener acceso directo: Drive externo al portal no obedece RBAC, estados de cuarentena ni revocaciones ATO. Credenciales Drive en archivo de servidor protegido fuera de Git, con tokens refrescados por biblioteca oficial y sin payloads en errores. No pedir secretos en chat ni conceder delegación de dominio por defecto. [ADR 0011](adr/0011-google-drive-evidencias.md).

## Controles adicionales M4

Contacto anónimo limita origen, tamaño, cuota durable HMAC y honeypot; no registra IP en claro ni refleja contenido. Aviso vigente obligatorio; email de privacidad aún pendiente, sin activación productiva. Fuente CF-Connecting-IP solo confiable en origen loopback dedicado y túnel configurado; no habilitar esa confianza sobre puerto accesible por red. Textos/fichas requieren aprobación explícita y fotos consentimiento/revisión visual. No públicos en Drive ni originales distribuibles. Export/backups cifrados con key escrow separado; sesiones restauradas se invalidan. Gmail/Tunnel preparados sin cuentas reales conectadas. [ADR](adr/0014-publicacion-contacto-operacion-m4.md), [gates operativos](22-operacion-m4.md).

Directorio de cuentas limitado a GERENTE y cuentas locales habilitadas; búsqueda POST en cuerpo y auditoría sin consulta/nombre/correo. La proyección de vinculadas exige autorización de la ficha antes de resolver identidades. La invitación usa un token de un uso con hash en DB; el enlace lo transporta en fragmento, retirado por el navegador y conservado temporalmente en sessionStorage para sobrevivir al login. Aceptar requiere el correo verificado coincidente; ni nombre ni correo del contacto crean membresías automáticas. [ADR 0015](adr/0015-cuentas-y-accesos-sin-identificadores-manuales.md).
