# 0010 — Obras, contratos y evidencias M1

**Estado:** Aceptado para el hito autorizado de desarrollo. **Fecha:** 8 de octubre de 2026. **HU:** HU-01/HU-04/HU-05/HU-11/HU-12. **BK:** BK-010 a BK-015. [Entrega](../17-entrega-m1.md) · [Contrato](../openapi/m1.json).

**Actualización posterior:** el usuario sustituyó S3 por Google Drive en [ADR 0011](0011-google-drive-evidencias.md). Las secciones S3 conservan evidencia histórica de M1; no describen el adaptador activo. El resto de decisiones empresariales sigue vigente.

## Contexto y decisiones

El usuario autorizó M1 y confirmó un cliente contratante por obra, representantes y asistentes con membresías explícitas, CRC inicial y S3 productivo con entorno local de pruebas. Declaró que todavía no existen responsable/contacto ni política de conservación aprobados. Se conserva Java 27/Boot 4.2.0-M2, el BOM, Maven raíz y el monolito modular.

`parties`, `projects`, `contracts` y `documents` continúan el acceso JDBC explícito de M0, con transacciones en servicios. Los controladores se comunican con servicios de su módulo. Las migraciones V3/V4 expanden el modelo; los recursos y grants sintéticos no habilitan obras. No se añaden reglas monetarias al frontend.

CLIENTE requiere membresía de su cliente y de la obra; PROVEEDOR, membresía del proveedor y asignación de obra; asistente, OPERATIONS explícito. Solo GERENTE administra esas relaciones con reautenticación. Una desactivación conserva datos. La asistente prepara borradores de obra solo para clientes de su ámbito existente y edita contactos operativos, sin cambiar identidad del contratante ni su estado.

Contrato base con neto/impuestos declarados manualmente, bruto derivado, dos decimales y CRC. No se supone tasa de IVA ni recuperabilidad. Después de formalizar con evidencia limpia, la base no admite cambios; enmiendas append-only derivan monto/plazo y corte `asOf`. Aceptación autenticada del representante o aceptación externa documentada por GERENTE se conserva por revisión; solo GERENTE formaliza. Una revisión nueva invalida la aceptación como autorización de la nueva revisión. No se afirma firma certificada. Cancelar un extra usa delta explícito y conserva el trabajo reconocido, evidencia e historial. No se generan cobros ni costos en M1.

AWS SDK Java v2 2.55.12 mediante `ObjectStoragePort`; SSE-S3, checksum SHA-256, PUT condicional, versión fijada en PostgreSQL y lectura de esa versión. Se recupera un PUT remoto confirmado cuyo commit local se perdió si el contenido es idéntico. El endpoint de carga es un proxy autenticado de un uso; no entrega una URL bearer a navegador. Todas las descargas revalidan permisos y hacen streaming HTTP de bytes verificados con `attachment` y `no-store`. No se exponen keys ni credenciales.

S3Mock 5.2.3 se ejecuta como proceso/servicio de pruebas separado del BOM de ATO. No valida IAM ni cifra realmente objetos. Se proponen bucket AWS privado, bloqueo de acceso público, versionado y políticas sin ListBucket/DeleteObject; cuenta/región y ensayo productivo pendientes. No se contrató un servicio externo.

ClamAV LTS 1.4.6 separado, con firmas oficiales actualizadas y antigüedad máxima 72 horas comprobada mediante VERSION. Escaneo INSTREAM, límites y fallos cerrados. La estructura de PDF/imagen/XML se inspecciona en un JVM hijo sin variables de entorno, heap máximo 192 MiB y plazo 15 segundos. PDFBox 3.0.8 limita páginas y rechaza acciones/adjuntos activos; imágenes verifican formato, decodificación y 40 megapíxeles; XML desactiva DTD/entidades/resolución externa y limita profundidad/nodos. No es una validación fiscal ni de firma.

La cola documental es el estado durable de `document`, con claim SKIP LOCKED, lease/token, backoff y revalidación del propietario al finalizar. Mantiene separada la FK sintética de outbox M0; no se borran o amplían grants existentes para acomodar negocio. Scanner caído, objeto corrupto o revocación no habilitan descargas. Reservas vacías caducan; originales/evidencias no se borran automáticamente.

## Privacidad y límites

Avisos versionados DRAFT/APPROVED; aprobar exige responsable, contacto y texto explícitos y reautenticación. Se conservan finalidad, medio, material, vigencia y evidencia del consentimiento y su retiro. Aviso aprobado y condiciones del consentimiento son inmutables; retirar no borra evidencias bajo hold. El frontend no contiene un aviso productivo inventado. Consentimiento no publica: M4 mantiene aprobación editorial separada.

Cancelación de una obra formalizada y cierre requieren liquidación de obligaciones de hitos posteriores; M1 solo cancela borradores sin base formalizada. USD, XML fiscal, costos/cobros y ejecución de Gantt continúan en sus hitos. No hay aprobación legal, despliegue, cuenta AWS ni borrado productivo implícitos.

## Verificación

Ver [17](../17-entrega-m1.md): PG 18.6, upgrade desde V2, cálculos/cortes/concurrencia/rollback, matrices de acceso, parser hostil, S3/ClamAV reales locales y flujo de navegador con Keycloak. IAM/TLS del destino productivo y recuperación operativa completa de M4 continúan pendientes.
