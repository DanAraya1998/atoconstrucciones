# 0003 — Objetos privados para evidencias

**Estado:** Sustituido por [ADR 0011 — Google Drive](0011-google-drive-evidencias.md), decisión del usuario del 8 de octubre de 2026. El texto siguiente conserva la propuesta histórica. **Fecha:** 7 de octubre de 2026. **HU:** HU-03/HU-10/HU-12. **BK:** BK-011/BK-012/BK-029. **Pendientes:** P-10/P-11. [Documentos](../10-documentos-e-integraciones.md).

## Contexto y propuesta

XML, PDFs e imágenes necesitan control de acceso y backup sin inflar DB. Guardar contenido en almacenamiento S3 privado mediante `ObjectStoragePort`; PostgreSQL conserva metadatos/hash/links/audiencia. Separar cuarentena/originales personales/empresariales y variantes públicas autorizadas.

MinIO Community tiene repositorio oficial archivado y declara ausencia de mantenimiento; no se selecciona por costumbre como base de producción. [Evidencia oficial](https://github.com/minio/minio). Evaluar S3 administrado mantenido o producto autoalojado con soporte/licencia definidos, no precios/obligaciones inventadas.

## Alternativas y consecuencias

Binarios en PostgreSQL simplifican atomicidad inicial, pero aumentan IO/copias/crecimiento. Disco local necesita replicación/recovery y limita despliegue. S3 requiere gestionar cuarentena, estados parciales y restore coordinado; API compatible no garantiza todas las funciones de versionado/locks del proveedor.

## Verificación

Seleccionar P-11 con costo/región/licencia/soporte revisados y pruebas reales de upload/checksum/URL privada/restore. T-FILE/T-RESTORE/T-RBAC. No aceptar el ADR antes de elegir proveedor/política recuperable, ni publicar originales por tener URL.
