# 10 — Documentos e integraciones

[Índice](README.md) · [Seguridad](11-seguridad-y-auditoria.md) · [Finanzas](08-reglas-financieras.md) · [API](06-api-y-openapi.md)

## Estado de cada capacidad

| Capacidad | Requisito / estado real | Selección pendiente |
|---|---|---|
| Evidencias XML 4.4, PDF e imágenes | Almacenamiento privado M1; importación fiscal M2 implementada localmente | Cuenta Drive/política definitiva pendientes; [bundle y pruebas M2](19-entrega-m2.md) |
| Google Drive privado | Confirmado por el usuario; adaptador backend implementado, conexión real pendiente | P-11: cuenta, carpeta, custodios, OAuth/Workspace, cuota y recuperación |
| Reportes/exportación PDF | Confirmada; fase 2 | Plantilla empresarial y librería probada con JDK 27 |
| Correo para invitación/recuperación/contacto | Propuesta transversal; starter presente, sin proveedor | Dominio/remitente/entrega y costos |
| Calendar | Confirmada para evaluar/desarrollar después; no conectada | P-08, scopes/consentimiento/dirección de sync |
| Importación/conciliación bancaria | Confirmada como capacidad planificada, sin muestras | P-09; ningún parser bancario inventado |
| Emisión/recepción tributaria online | Fuera del alcance actual | Nueva historia, revisión fiscal y ADR |

## Reglas documentales canónicas

- **RN-D01:** almacenar original privado con hash, tamaño, tipo detectado, autor, ámbito y versión; vínculos explícitos al recurso autorizado. XML/PDF/imagen de una factura son evidencias del mismo hecho, no tres gastos.
- **RN-D02:** cargas en cuarentena; validar tamaño, MIME real, extensión allowlist, formato, malware y cuotas antes de activar. Scanner indisponible→pendiente; no bypass en producción.
- **RN-D03:** XML sin DTD, entidades externas, XInclude ni acceso remoto a XSD; secure processing y límites de profundidad/expansión/tiempo. Resolver únicamente esquemas oficiales locales revisados.
- **RN-D04:** validación XML se divide en estructura XSD, consistencia de negocio, firma digital/estado tributario y autorización de imputación. Pasar XSD no demuestra aceptación de Hacienda ni pago bancario.
- **RN-D05:** original inmutable; reimportación no crea otra imputación ni sobrescribe diagnósticos. NC/ND referencian comprobante original; porción imputada y diferencia se justifican por obra.
- **RN-D06:** publicación requiere consentimiento específico/evidencia, aprobación editorial y variante sin EXIF/datos privados; retirar consentimiento impide nueva distribución controlada. Cierre no publica por defecto.
- **RN-D07:** integración externa necesita autorización real, mínimo payload, credenciales cifradas, outbox/idempotencia y tratamiento de revocación; jamás inferir consentimiento del rol local.

## XML de Costa Rica 4.4: evidencia oficial y límites

El catálogo oficial de Hacienda enumera esquemas 4.4 para factura, compra/exportación, tiquete, NC/ND, recibo de pago y mensajes. En la planificación se consultaron catálogo y anexos sin descargar el bundle. M2 obtuvo los XSD oficiales individuales de factura, tiquete, compra, NC y ND y su dependencia W3C; conserva URLs/bytes/SHA-256 en el [manifest](../src/main/resources/fiscal/v4.4/manifest.json). Otros tipos permanecen sin soporte fiscal. El PDF de catálogo devolvió 404 al descargarlo en esta sesión; los archivos individuales sí se obtuvieron, compilaron y probaron. [Estado y límites](19-entrega-m2.md). [Catálogo oficial](https://www.hacienda.go.cr/docs/Anexosyestructuras.pdf), [anexos 4.4](https://www.hacienda.go.cr/docs/ANEXOS_Y_ESTRUCTURAS_V4.4.pdf).

La explicación oficial del cambio de transitorio sitúa la entrada de disposiciones 4.4 el 1-09-2025; puede haber revisiones de anexos dentro de esa versión. Versionar tanto “4.4” como revisión/fecha/hash del bundle y volver a comprobar novedades oficiales antes de implementar. No usar esa fecha para rechazar todos los documentos históricos de otra versión: compatibilidad histórica se decide explícitamente. [Hacienda: modificación de transitorio](https://www.hacienda.go.cr/docs/MH-DGT-RES-0001-2025ModificTransitorioIResMH-DGT-RES-0027-2027.pdf).

Pipeline propuesto:

1. Autorizar upload por obra/ámbito; cuarentena y hash; identificar root y namespace sin resolver URLs embebidas.
2. Resolver tipo/versión/bundle local allowlisted. Validar estructura XSD con factory segura; no “intentar todos los esquemas hasta que pase”. Tipo no soportado→diagnóstico para revisión, no factura validada.
3. Extraer clave/identificadores como **strings**, emisor/receptor/actividad cuando aplique, fecha con offset, moneda/tasa, líneas, descuentos/cargos, impuestos/exoneraciones y totales. Validar requisitos por tipo y bundle; no copiar supuestos numéricos antiguos sobre cédulas o tamaños sin contrastarlos.
4. Detectar clave fiscal/identidad repetida y hash idéntico; asociar PDF/mensaje de Hacienda al comprobante existente. Clave idéntica con bytes/totales divergentes→bloqueo/revisión por integridad.
5. Comprobar sumas según esquema/tolerancia oficial vigente; preservar escala original. Identificación receptor distinta de entidad empresarial→alerta; no autoasignar un cliente por parecido de nombre.
6. Mostrar extracción y proponer gasto/imputaciones a obras/partidas, separando total fiscal, gasto operativo asignado y porción sin imputar. Totales dispares necesitan motivo y GERENTE; no decidir deducibilidad automáticamente.
7. Firma XML/mensaje Hacienda: almacenar resultados por separado (NO_EVALUADO/VALIDO/INVALIDO/NO_DISPONIBLE). Verificación criptográfica completa exige trust anchors, cadena/revocación/perfil y pruebas; el MVP puede quedar en NO_EVALUADO con revisión humana y sin rotular “aceptado por Hacienda”. No hacer consultas tributarias externas sin integración autorizada.
8. NC/ND: validar referencia/tipo/importe y generar propuesta de ajuste sobre costo original; revisión previa a efectos financieros. Recibo fiscal de pago tampoco confirma tesorería real.
9. Registrar versión del parser/bundle, diagnósticos de campo, actor de validación, reconocimiento resultante y documentos. Nunca convertir XML en movimiento bancario por tener `MedioPago`.

Seguridad de la configuración Java se ensaya con XXE y entidad expansiva; no basta definir una propiedad y asumir soporte en cualquier parser. [Guía oficial OWASP de prevención XXE](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html).

## Almacenamiento, límites y descargas

Google Drive API v3 detrás de `ObjectStoragePort`: originales binarios, fileId preasignado, revisión fijada y verificación de hash. PostgreSQL conserva referencias/vínculos/permisos; cargas y descargas pasan por ATO. Carpeta de evidencias privada con custodios acordados, sin permisos anyone/domain ni enlaces que omitan autorización. Credenciales de servidor separadas de Keycloak/Calendar; OAuth de usuario para Mi unidad o cuenta de servicio para unidad compartida. [ADR 0011](adr/0011-google-drive-evidencias.md) sustituye S3; [configuración y límites](18-almacenamiento-google-drive.md). Ámbito personal y variantes públicas requieren separación propia al implementar sus hitos.

Límites iniciales propuestos, configurables: XML 5 MiB, imagen 10 MiB y 40 megapíxeles decodificados, PDF 20 MiB/200 páginas, lote 50 MiB; cuotas por usuario/obra y tiempo/memoria de parsing. Son restricciones técnicas propuestas, no norma tributaria. “Otros comprobantes” necesitan tipo permitido y justificación registrada; nunca aceptar ejecutables/HTML/SVG activo por “otros”. MVP sin ZIP/OCR automático; un formato nuevo necesita política y pruebas.

Upload-session devuelve destino firmado breve y restricciones, object key aleatoria creada en servidor; complete-upload comprueba objeto real, no confía en tamaño/hash del body. Solo objeto limpio se vincula activo. Limpiar reservas abandonadas bajo política de temporales (propuesta 24 h); no borrar evidencias permanentes sin retención aprobada.

Download-authorization valida audiencia y recurso al emitir. Propuesta URL firmada ≤60 s y sin cache pública; una URL firmada es bearer hasta caducar, no puede prometer revocación individual instantánea. Para finanzas personales y grants de soporte usar streaming autorizado por petición, sin URL pública reutilizable. `Content-Disposition: attachment`, MIME controlado y preview derivado aislado de PDFs/imágenes. Registro audita emisión/acceso disponible; no registrar URL firmada completa. [OWASP file upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html).

## PDF y correo

Exportación PDF empresarial/personal: job con permisos por recurso, snapshot de filtros/corte/fórmulas/moneda, tablas legibles y descarga protegida. Candidato Apache PDFBox (Apache-2.0, proyecto activo con releases), sin web render de URLs arbitrarias; validar fuentes, tablas y Java 27 en BK-038. [Proyecto oficial PDFBox](https://pdfbox.apache.org/). No incorporar motores comerciales o fuentes no licenciadas implícitamente.

Correo: proveedor por elegir, plantillas en español, enlaces de invitación de un uso, SPF/DKIM/DMARC acordados con dominio real y reintentos outbox. Mensaje contiene mínimo dato y enlace a portal, no XML financiero/credenciales adjuntos. Estado ENCOLADO/ENVIADO/ERROR no demuestra lectura ni consentimiento. Contacto público se guarda antes de intentar email; notificar no hace perder solicitud por falla externa.

## Calendar: integración propuesta

Spring gestiona OAuth de Google separado del login OIDC de ATO: state/redirect exacto, consentimiento y scopes mínimos de eventos del calendario elegido, offline token solo si hace falta para jobs. Refresh/access tokens cifrados con clave fuera de DB; no enviarlos a Next/Flutter ni logs. Conectar/desconectar por GERENTE; revocación y error de autorización requieren reconexión, no escalada silenciosa de scopes. [Flujo web OAuth oficial](https://developers.google.com/identity/protocols/oauth2/web-server).

Crear evento en calendario autorizado e invitar proveedor es distinto de escribir en su calendario. `attendees`/envío de actualizaciones se habilitan solo por intención autorizada y alcance de contacto confirmado; no se presume aceptación. [Creación de eventos](https://developers.google.com/workspace/calendar/api/guides/create-events), [events.insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert).

Una vía ATO→Google inicialmente: mapping `(connection, calendarId, taskId)` único con eventId estable compatible, revisión sincronizada, etag remoto y estado. Después de timeout comprobar ID antes de reinsertar. Outbox actualiza/cancela con revisión y controles de evento; minimizar título/ubicación/descripción sensibles; no modificar Gantt desde un callback sin decisión bidireccional.

Si se amplía a dos vías: `syncToken` incremental, 410 invalida token y exige resincronizar, conflictos ATO/Google requieren regla explícita; webhooks se validan y solo disparan lectura, no son payload completo. Canales caducan y hay que renovarlos; deduplicar notificaciones. [Sincronización oficial](https://developers.google.com/workspace/calendar/api/guides/sync), [push notifications](https://developers.google.com/workspace/calendar/api/guides/push). Reintentos con backoff+jitter para 429/5xx; errores permanentes/de consentimiento se detienen, visibles a gerente. P-08 decide antes de BK-041/BK-042.

## Contratos de conciliación bancaria sin parsers inventados

Interfaz conceptual `BankStatementAdapter`: `supports(bank, mediaType, formatRevision)`, `inspect(fileMetadata)`, `parse(stream, context) -> StatementParseResult`. Contexto incluye cuenta/moneda/ámbito y configuración revisada; resultado contiene `NormalizedBankLine[]`, balance inicial/final **solo si fuente lo declara**, warnings, errores y ubicación de cada dato en fuente.

Línea normalizada: `sourceLineId?`, `operationDate`, `valueDate?`, `money` con signo/dirección, `reference?`, `description`, `sourceLocator`, `fingerprint`, `confidence`. No inventar columna, fecha, convención de signo, XML bancario estándar u OCR de un banco específico sin muestra anonimizadas y golden tests.

PDF estructurado/extracción textual se evalúa por adaptador; PDF escaneado queda pendiente o entrada manual auditada. CSV/XLSX/OFX o XML solo si banco/muestra justifican y licencia de parser se verifica antes de adoptar. Un archivo no soportado se conserva privado con diagnóstico, sin movimientos falsos.

Detectar lote repetido por cuenta/hash; ID de banco fuerte si existe; hash de fecha/monto/descripción es señal débil y no elimina dos operaciones iguales reales. Propuestas consideran moneda/monto/referencia/ventana de fechas, justifican score y residual; matching exacto tampoco se aplica definitivamente sin GERENTE. Conservar fuente y decisiones reversibles; vínculos no generan otra caja si depósito/pago ya está confirmado. [RN-F11 y RN-F12](08-reglas-financieras.md), BK-039/BK-040.

## Evidencias implementadas M1

Histórico M1: se confirmó inicialmente S3 y se probaron AWS SDK v2/S3Mock locales. La decisión posterior sustituye almacenamiento por Google Drive (ADR 0011); no se conserva S3 como proveedor activo. Carga proxy autenticada de un uso, lectura privada por petición, sin URL bearer. ClamAV real valida firmas frescas; parser JVM separado limita heap/tiempo, PDFBox rechaza acciones/adjuntos, ImageIO verifica tipo/decodificación/megapíxeles y StAX desactiva DTD/entidades/acceso externo. Un XML limpio almacenado no es una validación fiscal: BK-020/BK-021 permanecen pendientes.

Estados RESERVED/QUARANTINED/CLEAN/INFECTED/REJECTED/EXPIRED. Lease y reintentos durables, escáner caído permanece QUARANTINED, revocación al ejecutar bloquea resultado. Links limitados a la misma obra; evidencia contractual no se comparte con proveedor. Holds preservan evidencia y retiro no borra hechos. Reserva vacía caduca en una hora; no hay eliminación permanente/huérfanos automática sin política aprobada. [Implementación, selección y ensayos](17-entrega-m1.md).

## Publicación y Gmail M4 implementados

[ADR 0014](adr/0014-publicacion-contacto-operacion-m4.md) fija consentimiento web PORTFOLIO/WEBSITE, variante pixel-only JPEG en Drive privado y aprobación visual. Solo variantes aprobadas se distribuyen por API con autorización vigente y `no-store`; previews privados y optimizador Next restringido al logo. Retirada/caducidad bloquean próximas lecturas sin purga diferida. El usuario eligió Gmail SMTP; adaptador STARTTLS/reintentos preparado, cuenta/remitente/credenciales reales pendientes. Contacto+delivery durable antes del intento externo, notificación mínima y estado sin garantía de lectura; posible duplicado SMTP documentado. [Operación](22-operacion-m4.md).
