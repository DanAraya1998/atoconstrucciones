# 0011 — Google Drive para evidencias

**Estado:** Aceptado por instrucción del usuario; conexión real pendiente de configuración del cliente. **Fecha:** 8 de octubre de 2026. **HU:** HU-03/HU-10/HU-12. **BK:** BK-011/BK-012/BK-029. Sustituye [ADR 0003](0003-documentos-en-objetos.md) y la selección de S3 de [ADR 0010](0010-obras-contratos-evidencias-m1.md).

## Contexto y decisión

El cliente ya opera con Google Drive y el usuario solicita usarlo como almacenamiento de ATO. El backend implementa Drive API v3 detrás de `ObjectStoragePort`. PostgreSQL mantiene recursos, permisos, hashes y referencias de archivo/revisión; se conservan uploads autenticados, cuarentena, antivirus y descargas autorizadas por ATO. Keycloak y la conexión futura de Calendar son independientes de las credenciales Drive del backend.

La conexión admite credenciales OAuth de usuario para Mi unidad y cuenta de servicio para una unidad compartida Workspace, sin delegación de dominio automática. Cuenta, carpeta y custodios definitivos pendientes de confirmar. Una cuenta de servicio no tiene cuota para ser propietaria de archivos en Mi unidad: [documentación oficial](https://developers.google.com/workspace/drive/api/guides/about-shareddrives).

## Integridad y recuperación

La referencia Drive no es una key S3. Se preasigna un ID mediante `files.generateIds` y se persiste en una transacción independiente antes de crear el archivo: un timeout o rollback del comando no genera un archivo diferente en cada retry. El contenido es binario original, sin conversión a Docs/Sheets. Se fija la revisión con `keepForever`, se lee esa revisión y se verifica SHA-256. No se actualiza contenido existente ni se borran evidencias automáticamente. Drive conserva como máximo 200 revisiones fijadas por archivo; ATO crea un archivo por evidencia: [revisiones](https://developers.google.com/workspace/drive/api/guides/manage-revisions), [creación y reintentos](https://developers.google.com/workspace/drive/api/guides/create-file).

La carpeta de ATO debe ser privada, accesible solo a los custodios acordados y al backend. No se crean permisos públicos ni se envían enlaces Drive al portal. Los permisos heredados de Drive siguen teniendo efecto fuera de ATO: un usuario con acceso directo puede saltarse las restricciones del portal, incluida cuarentena; revisar los custodios es condición de operación. Los cambios manuales o pérdidas de acceso no habilitan descargas alternativas.

Migración expansiva: registros documentales existentes quedan marcados S3; los nuevos usan GOOGLE_DRIVE. No reinterpretar versiones S3 como revisiones Drive, limpiar volúmenes ni copiar documentos reales sin un inventario y verificación de hashes/vínculos. No se aplica ninguna migración a una cuenta remota en esta entrega.

## Autenticación y alcance

Credenciales fuera de Git, cargadas por la biblioteca oficial Google Auth, con refresh de tokens en servidor. Priorizar `drive.file`, que requiere archivos creados o seleccionados para la aplicación; conocer un ID de carpeta no concede ese acceso. Una carpeta existente necesita selección/consentimiento con el mismo cliente OAuth o acceso Workspace comprobado. Un scope más amplio exige decisión específica y revisión; no se solicita por defecto: [scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).

## Verificación y límites

Pruebas locales del protocolo HTTP Drive y PostgreSQL cubren idempotencia/rollback, revisión fijada, integridad, errores y restricciones de acceso. No son un emulador completo ni una conexión Google real. Ensayo de cuenta/carpeta/permisos, cuota, refresh/revocación y recuperación coordinada DB+Drive pendientes hasta disponer de configuración autorizada. Drive no sustituye un backup ni equivale a retención inmutable de evidencias frente a administradores.
