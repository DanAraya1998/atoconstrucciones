# 18 — Almacenamiento Google Drive

[Índice](README.md) · [ADR 0011](adr/0011-google-drive-evidencias.md) · [BK-011/BK-012](13-roadmap-y-backlog.md) · [Arranque](../README.md)

## Decisión y estado

El usuario informa que el cliente ya utiliza Drive y solicita sustituir S3. El adaptador Google Drive API v3 está implementado; no hay SDK AWS ni servicio S3Mock activos. Se conservan PostgreSQL, permisos por recurso, cuarentena, ClamAV, hash y descarga autenticada. Los contratos HTTP y pantallas empresariales no cambian.

Cuenta, carpeta y conexión reales pendientes de confirmar. Las pruebas de protocolo son un fixture HTTP local, no una ejecución Google Drive real. No se ha accedido al Drive del cliente ni se han trasladado archivos o cambiado permisos remotos.

## Configuración del backend

Primero habilitar Drive API en un proyecto Google autorizado y escoger una carpeta de evidencias ATO restringida a custodios acordados. Conservar los archivos existentes del cliente; esta implementación almacena nuevas evidencias, no importa automáticamente todas sus carpetas.

| Cuenta | Credencial admitida | Condición |
|---|---|---|
| Mi unidad, personal o Workspace | OAuth de usuario, JSON `authorized_user` con refresh token | Consentimiento del propietario, mismo cliente OAuth y acceso comprobado a la carpeta |
| Unidad compartida Workspace | JSON `service_account` con permisos sobre esa unidad | Carpeta dentro de una unidad compartida; no usar la cuenta de servicio como propietaria en Mi unidad |

Las cuentas de servicio no tienen cuota para ser propietarias de archivos; requieren unidad compartida u OAuth actuando por un usuario. No se implementa delegación global de dominio. [Google: unidades compartidas](https://developers.google.com/workspace/drive/api/guides/about-shareddrives).

Configurar `.env` con valores reales locales; las siguientes son variables de la plantilla, no credenciales funcionales:

```dotenv
ATO_STORAGE_ENABLED=true
ATO_DRIVE_FOLDER_ID=ID_DE_CARPETA_AUTORIZADA
ATO_DRIVE_CREDENTIALS_FILE=/ruta/privada/credenciales-drive.json
ATO_DRIVE_SCOPE=https://www.googleapis.com/auth/drive.file
ATO_SCANNER_HOST=127.0.0.1
ATO_SCANNER_PORT=3310
```

El archivo de credenciales queda fuera del repositorio o en `.m0-local/`, con permisos restrictivos y sin mostrar su contenido en logs/chat. Se aceptan únicamente los dos tipos indicados y endpoints de token Google; no archivos de credenciales subidos por visitantes. Google Auth refresca los access tokens en servidor. No son las credenciales de Keycloak ni de Calendar. Reiniciar el backend tras configurar.

`drive.file` es el scope predeterminado. Conocer un ID de carpeta no otorga acceso: una carpeta existente debe haber sido seleccionada/consentida para **la misma aplicación OAuth** (por ejemplo, Google Picker), o creada por ella. El adaptador no implementa todavía esa pantalla de selección ni un asistente de consentimiento. La conexión autorizada inicial se realiza de forma administrada; no basta con exportar un access token temporal. OAuth de usuario conserva los scopes que se concedieron al obtener el refresh token; cambiar una variable no amplía su consentimiento. Un scope `drive` más amplio se admite solo para configuración administrada explícita, requiere revisar su alcance y las condiciones de verificación; no se solicita automáticamente. [Google: scopes y refresh tokens](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).

Hasta disponer de esta configuración, mantener `ATO_STORAGE_ENABLED=false`: obras/cartera siguen accesibles y las cargas fallan con 503. Si `.env` activaba el emulador S3 anterior, desactivar temporalmente ese flag y retirar sus variables `ATO_STORAGE_BUCKET/REGION/ENDPOINT/PATH_STYLE/ACCESS_KEY/SECRET_KEY`; no se interpretan como credenciales Drive. Un almacenamiento activado con credenciales/carpeta incompletas impide el arranque y no habilita un fallback inseguro.

## Originales, revisiones y permisos

Se crea un archivo binario por evidencia en la carpeta configurada, identificado por UUID y nombre original. No se convierten XML/PDF/imágenes a Docs/Sheets. `files.generateIds` preasigna un ID que PostgreSQL reserva antes de la llamada de upload y fuera de la transacción documental. Timeout o rollback conservan esa referencia; retry utiliza el mismo ID y verifica contenido antes de confirmar. No hay búsquedas por nombre ni reemplazos de archivos existentes.

La revisión binaria se fija con `keepForever`; el documento mantiene `drive:fileId:revisionId` internamente. Se descarga esa revisión y se comprueban tipo, tamaño y SHA-256. Una edición manual de la cabeza del archivo no sustituye la evidencia fijada. Google tiene un límite de 200 revisiones fijadas por archivo; ATO no actualiza evidencias en el mismo archivo. [Creación y IDs](https://developers.google.com/workspace/drive/api/guides/create-file), [revisiones](https://developers.google.com/workspace/drive/api/guides/manage-revisions).

Cargas siguen en QUARANTINED hasta completar antivirus/inspección. El adaptador rechaza permisos `anyone`/`domain`, carpeta/archivo en papelera, padre cambiado, revisión sin fijar, hash incorrecto, acceso revocado y redirecciones. No crea permisos públicos, no borra archivos ni devuelve links Drive al portal. Errores de cuota/red/permisos mantienen bloqueo con respuesta sanitizada.

Los custodios con acceso directo a Drive pueden leer archivos sin pasar por RBAC o cuarentena de ATO. La integración no puede revocar ese acceso al revocar un usuario de ATO. Por eso la carpeta debe excluir clientes/proveedores/asistentes que no deban leer todo su contenido; revisar también los miembros de grupos y permisos heredados. No se presenta Drive como almacenamiento inmutable frente a administradores ni como sustituto de backup.

## Datos S3 anteriores

V5 es expansiva y no modifica V1–V4. Agrega `document.storage_provider`: filas anteriores quedan S3 y nuevas reservas GOOGLE_DRIVE. Las referencias/estados/vínculos existentes se conservan; su descarga retorna `STORAGE_MIGRATION_REQUIRED` en vez de leer una versión S3 como revisión Drive.

No limpiar volúmenes, borrar objetos ni ejecutar el antiguo inicializador. `scripts/init-local-storage.py` ahora explica el cambio y sale sin modificar datos; las plantillas `infra/s3-*.json` quedan históricas y no se aplican. El cambio no hace una copia remota automática.

Cuando existan datos que trasladar: inventariar documentos/versiones/hashes/holds, obtener originales con el proveedor anterior, crear en la carpeta Drive autorizada, fijar revisión y verificar tamaño/hash, y solo entonces actualizar proveedor/referencia en una transacción auditada conservando documentId y vínculos. Ensayar rollback y guardar manifiesto de correspondencias antes de retirar el origen. La reserva técnica Drive también debe respaldarse; copiar archivos puede generar IDs nuevos y requiere reconciliar referencias. Este procedimiento real está pendiente, no se presenta como migración ya ejecutada.

## Pruebas y evidencia

`ATO_M1_INFRA=1 ./mvnw --batch-mode --no-transfer-progress -Dmaven.repo.local=/tmp/ato-m0-maven clean verify` ejecutado el 8 de octubre de 2026: **47 pruebas contabilizadas, 44 ejecutadas con éxito y 3 omitidas**, cero fallos/errores. Incluye PostgreSQL 18.6 real, nueve pruebas HTTP de Drive, upgrade V4→V5 con documento S3 preservado, controles M0/M1 y ClamAV real (limpio/EICAR/indisponibilidad). JAR generado.

Las tres omitidas son [DocumentInfrastructureTests](../src/test/java/com/daraya/atoconstrucciones/DocumentInfrastructureTests.java): requieren `ATO_DRIVE_LIVE_TESTS=1`, carpeta y credenciales de **pruebas** autorizadas, y ClamAV real. Crean evidencias sintéticas y las conservan; no ejecutar esa bandera sobre el Drive operativo del cliente. Sin `ATO_M1_INFRA`, también se omite explícitamente el ensayo ClamAV local.

CI común usa protocolo Drive local + PG/ClamAV y OIDC; no necesita credenciales Google. El E2E empresarial `ATO_E2E_M1=1` requiere el backend conectado a la carpeta Drive de pruebas. Su ejecución con Drive real, validación de consentimiento/refresh/revocación/ACL/cuota y restore coordinado continúan pendientes. El E2E S3 anterior de 17 es evidencia histórica, no verificación de Drive.

Google Auth Java **1.54.0**, BSD-3-Clause según [POM oficial](https://repo.maven.apache.org/maven2/com/google/auth/google-auth-library-parent/1.54.0/google-auth-library-parent-1.54.0.pom), resuelto desde Central y compilado/probado con Java 27 y el BOM Boot. Biblioteca mantenida en Google Cloud Java; [guía oficial](https://docs.cloud.google.com/java/getting-started/getting-started-with-google-auth-library). Drive REST utiliza HttpClient JDK con origen Google fijo, sin redirecciones, timeout y acumulación de respuesta acotada. No se añadió un SDK Google Cloud Storage ni se sustituyó Drive por otro servicio.
