# 17 — Entrega M1

[Índice](README.md) · [Roadmap](13-roadmap-y-backlog.md) · [Arranque](../README.md) · [Contrato M0 + M1](openapi/m1.json) · [ADR](adr/0010-obras-contratos-evidencias-m1.md)

**Histórico de la entrega inicial M1.** La selección S3 y sus ensayos se sustituyeron posteriormente por Google Drive a petición del usuario. Estado vigente, configuración, migración y nuevas pruebas en [18](18-almacenamiento-google-drive.md); no usar los pasos S3 de este capítulo para iniciar el proyecto actual.

## Alcance implementado

El 8 de octubre de 2026 el usuario autorizó M1 y confirmó el alcance inicial: un contratante por obra, membresías explícitas, CRC, S3 productivo con pruebas locales y privacidad en borrador porque aún no existen responsable ni políticas aprobados.

| Tarea | Implementación revisable | Límites |
|---|---|---|
| BK-010 | Clientes/proveedores, contactos, desactivación, membresías por parte/obra, ficha/fechas/coordenadas, estados y ETag; cartera y obras en Next | Cancelación de obra formalizada requiere liquidación posterior; representantes múltiples del mismo contratante permitidos, sin varios contratantes |
| BK-011 | Adaptador AWS S3, checksum, SSE, versión fijada, cuarentena, ClamAV real con firmas frescas y parser acotado; entorno S3Mock aislado | Cuenta/región/precio/IAM/TLS/restore del bucket productivo pendientes; S3Mock no prueba estos controles AWS |
| BK-012 | Reserva de un uso, upload autenticado/complete, audiencia/links/holds, estado visible y descarga protegida por petición | Reservas vacías caducan; no hay borrado de originales ni URLs firmadas. Solo ámbito empresarial de M1 |
| BK-013 | Avisos versionados, aprobación explícita, solicitudes de acceso/rectificación/retiro, consentimiento por fotografía/finalidad/medio/vigencia y retirada conservando evidencia | Responsable, contacto, texto y retención definitiva pendientes por decisión expresa; ninguna publicación ni plazo legal inventado |
| BK-014 | Base en CRC con neto/impuestos/bruto, formalización con evidencia, base inmutable, enmiendas y corte histórico | USD en BK-037; no se interpreta fiscalmente el impuesto declarado ni se generan cobros/costos |
| BK-015 | Solicitud, revisiones, aceptación de versión vigente, aprobación única, ejecución/terminación y cancelación con delta vinculado; pantalla e historial | Aceptación del portal no se presenta como firma certificada; liquidación/costos futuros no se borran por cancelar |

Pantallas: `/cartera`, `/obras`, `/obras/{id}`, `/privacidad`; entrada desde el espacio privado de M0. Los importes siguen strings exactos y se presentan en es-CR; fechas civiles e instantes se distinguen y usan America/Costa_Rica.

## Reglas y controles

ADMIN no obtiene acceso empresarial por su rol o por un grant sintético. CLIENTE y PROVEEDOR requieren las dos relaciones activas correspondientes; revocar una impide lecturas/listas/children/descargas posteriores. PROVEEDOR recibe ficha mínima sin cliente/contacto empresarial ni contrato/extras. Asistente consulta por obra y prepara; gerencia formaliza importes, estados, accesos y audiencias con contraseña de los últimos cinco minutos. La política opcional de MFA de M0 se conserva desactivada.

Cada comando crítico mantiene idempotencia, lock/versión y auditoría en la transacción. Reintento con igual clave/body devuelve resultado previo; otro body da 409; ETag obsoleto 412 y faltante 428. PostgreSQL impide modificar base formalizada, enmiendas, revisiones, aceptación y avisos aprobados. Auditoría guarda motivo, versiones y referencias donde corresponden sin payload documental ni URLs privadas.

Escaneo y validación no confían en extensión ni hash del cliente: se verifican bytes/hash/tipo reales. Un archivo limpio conserva su versión; una versión nueva en S3 no sustituye la evidencia fijada. Descargar vuelve a verificar hash y permisos. Documento incompleto/infectado/rechazado o escáner sin disponibilidad/firmas frescas permanece bloqueado. Los documentos financieros no se comparten con proveedores. PDF activo, XML con DTD/XXE, imagen falsa, límites de contenido y hash distinto se rechazan. No hay importación fiscal en M1.

## Dependencias y evidencia de selección

Se conserva el BOM Boot. Dependencias adicionales verificadas antes de resolver: AWS SDK Java **2.55.12**, Apache-2.0, [release oficial](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.55.12), [licencia](https://github.com/aws/aws-sdk-java-v2/blob/master/LICENSE.txt), [checksums oficiales](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/s3-checksums.html) y [advisories](https://github.com/aws/aws-sdk-java-v2/security/advisories). No se añade el módulo CloudFront afectado por su aviso. Cliente HTTP JDK del SDK evita introducir Netty/Apache HTTP ajenos al BOM.

PDFBox **3.0.8**, Apache-2.0, [release/licencia](https://pdfbox.apache.org/download.html) y [avisos](https://pdfbox.apache.org/security.html); versión corregida de los ejemplos afectados, que no se incorporan. El parser hijo usa límites de tiempo/memoria; no se considera validación legal de firmas ni sandbox de sistema operativo.

S3Mock **5.2.3**, Apache-2.0, [proyecto y alcance](https://github.com/adobe/S3Mock), [release](https://github.com/adobe/S3Mock/releases/tag/5.2.3); solo servicio local/CI. ClamAV **1.4.6 LTS**, GPLv2, [versiones oficiales](https://www.clamav.net/downloads), [política LTS](https://docs.clamav.net/faq/faq-eol.html), [instalación y verificación](https://docs.clamav.net/manual/Installing.html). Paquete local verificado con firma Talos, fingerprint `5BAD CA26 65EF 59DC F8A2 3D8B 707F 0DB4 8083 6771`; firmas oficiales actualizadas con FreshClam. No se instala antivirus en el sistema ni se redistribuye su binario en Git.

Los servicios S3/antivirus corrieron como procesos aislados en loopback, ya que esta máquina no tiene daemon Docker. Compose/CI están configurados; no se afirma ejecución de Compose ni CI remota.

## Validación ejecutada

Resultados locales del 8 de octubre de 2026, con recursos y cuentas sintéticos:

| Verificación | Resultado ejecutado |
|---|---|
| `ATO_M1_INFRA=1 ./mvnw --batch-mode --no-transfer-progress clean verify` (caché Maven aislada) | **35 pruebas**, 0 fallos/errores/omisiones; JAR empaquetado. 17 de fundamentos M0, 14 de negocio M1, 3 de infraestructura documental real y 1 de upgrade |
| PostgreSQL 18.6 / Flyway | Actualización V2→V3/V4 conserva identidad/auditoría/recurso M0; cuatro migraciones validadas; arranque final con `ddl-auto=validate` |
| S3Mock + ClamAV reales | XML limpio, EICAR detectado, scanner no disponible bloquea; checksum, PUT condicional, versión fijada ante reemplazo y copia de restauración |
| Parser en JAR | JVM hijo vía PropertiesLauncher verifica XML; flujo real de carga llega a CLEAN con memoria/tiempo acotados |
| Frontend Node 24.21.0 | Generación API, lint, TypeScript y build Next correctos; rutas de cartera, obras, ficha y privacidad junto a M0 |
| Transporte TypeScript | `npm run test` correcto; ejecución explícita con `node --experimental-strip-types --test --test-isolation=none` reporta **3 pruebas**, 0 fallos, sobre dinero/CSRF/idempotencia/errores |
| Playwright / Keycloak real | **5 pruebas**, 0 fallos/omisiones, con `ATO_E2E_KEYCLOAK=1 ATO_E2E_M1=1`: contrato/extra/evidencia privados, aceptación cliente/aprobación gerente, privacidad, revocación, móvil/teclado e hidratación SVAR |
| Copia DB + objetos local | Backend detenido durante la copia; dump/restore a DB nueva conserva cuatro migraciones, dos contratos, dos enmiendas y seis vínculos; copia S3 servida en otro puerto devuelve **2 evidencias CLEAN** con versión/tamaño/SHA-256 coincidentes con DB restaurada |
| Contratos/documentación/config | Baseline OpenAPI M0 compatible, referencias y secretos obvios correctos; **3 pruebas** de fallos controlados del checker; JSON M1 incluido en JAR idéntico a fuente; `docker compose --profile m1 config --quiet` correcto; inicializador local verifica versionado |

El navegador ejercita el backend final empaquetado, no un mock de los comandos M1. La suite de negocio usa adaptadores controlados para inyectar fallos; las tres pruebas de infraestructura y el navegador usan S3/antivirus reales locales. Las capturas de escritorio y móvil están en `frontend/test-results/`, excluidas de Git. Una primera ejecución del nuevo paso de privacidad falló por un selector exacto del campo Tipo; corregido el selector, las cinco pruebas completas pasaron.

El ensayo de restore valida estas referencias sintéticas en S3Mock 5.2.3; no demuestra recuperación de cuenta AWS, claves, IAM, IdP ni cumplimiento de RPO/RTO. No se ejecutó CI remota ni Compose con daemon Docker en esta máquina.

### Corrección del entorno local tras la entrega

La instancia nativa de pruebas quedó escuchando en 54329 con usuario/base `postgres`, mientras `.env` utiliza usuario/base `ato`. El arranque manual produjo `FATAL: role "ato" does not exist` antes de ejecutar migraciones. Se creó el rol local `ato` sin privilegios de superusuario, con la contraseña existente de `.env`, y una base independiente `ato` de su propiedad. Se conservaron las bases y evidencias sintéticas anteriores, sin cambiar `.env` ni migraciones. El backend arrancó con esa configuración y aplicó V1–V4 correctamente; se detuvo el proceso de verificación para dejar libre 8080. La instancia nativa es temporal en `/tmp`; no debe ejecutarse simultáneamente con el PostgreSQL de Compose en el mismo puerto.

## Pendientes antes de producción

Responsable/contacto, revisión del aviso y retención por clase, encargados y obligaciones de conservación siguen pendientes. El intento de obtener texto completo actual de Ley 8968/PRODHAB encontró 502/403 y SINALEVI expone una página dinámica sin texto completo en el navegador disponible; no se presenta como revisión legal consolidada. Fuentes y límites en [11](11-seguridad-y-auditoria.md).

Configurar bucket real privado, región, versionado, bloqueo de acceso público, SSE, IAM mínimo, firmas actualizadas y pruebas reales de políticas/costo/restore. [Plantillas](../infra/s3-application-policy.json) y [política de bucket](../infra/s3-bucket-policy.json) son revisables, sin aplicar a una cuenta. La recuperación completa de DB+objetos+claves productivos sigue en BK-029. Boot preview debe revalidarse con release estable antes de producción. M2–M6, SMTP y despliegue siguen fuera de esta entrega.
