# 21 — Entrega M4

Evolución visual 27/10/2026: [rediseño público y lorem ipsum](27-rediseno-publico-y-lorem-ipsum.md), CA-00.6. Se permite contenido de muestra/conceptos identificados; las fotos empresariales siguen requiriendo aprobación/consentimiento. El optimizador añade tres paths estáticos exactos al logo y conserva bloqueadas las fotos del API. Las evidencias M4 siguientes son históricas; el documento 27 registra los nuevos ensayos y límites.

[Índice](README.md) · [Backlog](13-roadmap-y-backlog.md) · [Contrato M0–M4](openapi/m4.json) · [ADR 0014](adr/0014-publicacion-contacto-operacion-m4.md) · [Runbook](22-operacion-m4.md)

El 9 de octubre de 2026 el usuario autorizó M4. Implementación y pruebas locales; exposición pública y aceptación productiva pendientes. Se conserva M0–M3, Java 27/Boot 4.2.0-M2, PostgreSQL y Google Drive. Hosting local + Cloudflare Tunnel, dominio `atoconstrucciones.com`, Gmail SMTP y RPO 24 h / RTO 4 h confirmados. Responsable del aviso definida en la sesión y configurable en privado; correo del aviso todavía pendiente.

## Capacidades

| BK | Entrega revisable | Límite explícito |
|---|---|---|
| 025 | Inicio/ATO/Proyectos/ficha/Contacto/Login/aviso, navegación móvil nativa, metadata/canonical/robots/sitemap, textos aprobados, imágenes dimensionadas | Contenido corporativo real requiere aprobación; sin fotos/cifras del PDF ni imágenes ficticias presentadas como obras |
| 026 | Borrador/revisión/publicación/retirada, foto y consentimiento por asset, variantes sin metadatos en Drive privado, revisión visual, historial/ETag/idempotencia/auditoría | API hasta 12 fotos; editor inicial una portada, conserva selección al editar. Portafolio independiente reutiliza custodia privada y no fabrica historia financiera |
| 027 | Contacto anónimo con aviso vigente, validación/honeypot/cuota/origen/tamaño, recepción durable y SMTP Gmail con lease/reintentos/DEAD/reencolado; bandeja/delegación | Cuenta/remitente/credencial real y entrega Gmail pendientes; formulario permanece cerrado sin aviso aprobado; SMTP puede duplicar notificación tras caída remota |
| 028 | Suites anteriores y nuevos flujos HTTP/UI, permisos, migración, Axe/teclado/móvil y medición local | Métricas locales no acreditan rendimiento de hosting/edge ni certificación WCAG/lector de pantalla |
| 029 | JAR/standalone, manifiesto de release, plantilla Tunnel/entorno, CLI DB+objetos cifrados, restore aislado y alertas técnicas | Cuenta Drive/IdP, backup externo, claves productivas, imágenes/runtime y operación en hosting real pendientes; no se desplegó |

## Controles

`/api/v1/public` usa DTO anónimo sin obra/cliente/contrato/saldos/dirección exacta/original/credenciales. Texto comercial no aprobado no sale al público. GERENTE aprueba publicación con contraseña ≤5 minutos y revisión visual explícita. Asistente prepara solo obras con OPERATIONS vigente; ADMIN no obtiene funciones empresariales. Contactos globales solo gerencia o asistente delegada; lectura auditada sin contenido personal en auditoría.

Fotos requieren PHOTO/CLEAN, consentimiento exacto PORTFOLIO/WEBSITE, aviso aprobado y evidencia limpia. JPEG hasta 1600 px se reencode desde píxeles nuevos en JVM hijo sin credenciales, memoria/tiempo acotados. Variant key `business/variants/{uuid}` conserva la política del adaptador Drive. API verifica SHA/tamaño y consentimiento en cada distribución; expiry usa Costa Rica. Retirada conserva original/consentimiento/historia y bloquea asset/lista/ficha sin depender de worker. `no-store` y ausencia de cache CDN controlada; Next restringe `/_next/image` al logo para impedir la copia alternativa en caché.

Contacto guarda solicitud y cola en la misma transacción antes de SMTP. Reintento del mismo UUID/body devuelve referencia previa; cuerpo distinto 409. Origin exacto y JSON ≤16 KiB; cuota 8/10 min por fuente HMAC y honeypot. Mensajes no reflejan HTML ni enumeran cuentas; React escapa la consulta privada. Gmail STARTTLS obligatorio, remitente/destino de configuración, notificación mínima sin datos del visitante. No se presenta SENT como lectura; mail sin configurar conserva PENDING sin consumir intentos. Lease perdido no permite confirmar con token viejo; reintentos agotados visibles/reencolables por gerencia.

V8 expansiva; las migraciones históricas permanecen intactas. OpenAPI M4/cliente generado preservan M3 y documentan nuevos errores, estados, permisos, origen y caché. La única excepción pública de CSRF es contacto con comprobación Origin; las mutaciones privadas siguen protegidas.

## Verificación ejecutada

Validación local del 9 de octubre de 2026:

| Comprobación | Resultado ejecutado |
|---|---|
| Maven `--offline verify`, Java 27, PostgreSQL 18.6 | 99 casos: **95 pasan**, 4 opt-in de infraestructura omitidos; cero fallos. Incluye M0–M4, actualización V7→V8, cifrado/manipulación de copias y transporte Gmail controlado |
| Next build de navegador y final normal | Correctos; el artifact final apunta al backend habitual 8080, sin override del harness |
| ESLint / TypeScript / transporte Node / peers | Correctos; `npm ls --all` sin conflictos. Node local 26.10.0 dentro del rango del proyecto; CI conserva 24.21.0 |
| Auditoría npm | 0 vulnerabilidades informadas con lockfile M4; no sustituye scanner de runtime ni revisión de Boot preview |
| Playwright M0–M4 con Keycloak real | **14 flujos pasan**, 1 smoke restore opt-in omitido en esa corrida; M4 se reejecutó tras añadir aislamiento de ADMIN y sus 3 flujos pasan |
| Playwright sobre destino restaurado | **1 flujo adicional pasa**: login nuevo, original SHA-256, preview JPEG, contactos, cliente propio/ajeno, proveedor y ADMIN con permisos conservados |
| Axe / teclado / móvil / revisión visual | Cero violaciones automatizadas en seis rutas públicas y ficha autorizada, tags WCAG 2/2.1/2.2 AA; menú/skip link/errores y ancho 360 px. Inicio revisado visualmente a 360/1280 px |
| Lighthouse móvil local, Inicio con textos sintéticos aprobados | Performance/accesibilidad/SEO **100/100/100**, LCP **1,73 s**, CLS **0**, TBT **30 ms** en informe conservado `target/m4-quality/`. Una corrida previa obtuvo LCP 0,94 s; métricas de laboratorio variables, sin medir INP de campo |
| Copia/restore DB+archivos+clave sintéticos | **60 tablas**, **8 migraciones**, **18 originales/variantes**; hashes de filas y objetos iguales. AES-GCM/manifest autenticado, clave mode 600 separada, sesiones restauradas invalidadas; **14,392 s** para copia y verificación, fuera del arranque/smoke |
| Contratos / documentos / secretos | Compatibilidad contra M3, enlaces y checker de secretos correctos; 3 pruebas de fallos controlados del checker pasan |

La copia final conserva contratos/enmiendas, cobros/pagos/devengo, depósitos, cronograma/dependencias/asignaciones/pesos/avance, documentos/consentimientos, fichas/historia/contactos y cuentas/ACL. Backend restaurado arrancó con workers/SMTP apagados; se comprobó distribución pública bloqueada por consentimiento y acceso privado íntegro. Se conservan copias nuevas y referencias de destino bajo `target/`; detener el harness cierra su PostgreSQL temporal. No son datos empresariales reales.

No contar como ejecutados Drive/Gmail/Cloudflare productivos, CI remota, Docker/ClamAV opt-in omitidos, carga representativa 200 obras/500 tareas ni prueba manual con lector de pantalla. El tiempo local no demuestra RPO/RTO del hosting real. Release preview y gates productivos siguen abiertos.

## Reproducir

Con Keycloak de pruebas, preparar clases y arrancar harness aislado; mantener Maven terminado antes de arrancar el servidor:

```bash
./mvnw --batch-mode --no-transfer-progress test-compile dependency:build-classpath -Dmdep.outputFile=target/m2-test-classpath.txt
python3 scripts/run-m2-browser.py --milestone m4
```

Con 3000 libre:

```bash
ATO_BACKEND_ORIGIN=http://127.0.0.1:9192 npm run build --prefix frontend
ATO_BACKEND_ORIGIN=http://127.0.0.1:9192 ATO_E2E_M1=1 ATO_E2E_M2=1 ATO_E2E_M3=1 ATO_E2E_M4=1 ATO_E2E_KEYCLOAK=1 npm run test:e2e --prefix frontend
python3 scripts/verify-m2-restore.py --milestone m4
```

El harness M4 persiste únicamente archivos sintéticos en `target/m4-browser-objects`, para comprobar copias de originales y variantes. No es almacenamiento de aplicación ni Drive real. Detener procesos y reconstruir frontend sin override para volver a 8080. Configuración/recuperación productiva en [22](22-operacion-m4.md).

Para smoke funcional, mantener el harness origen activo porque conserva el PostgreSQL temporal:

```bash
python3 scripts/run-m4-restored.py
ATO_BACKEND_ORIGIN=http://127.0.0.1:9292 npm run build --prefix frontend
ATO_BACKEND_ORIGIN=http://127.0.0.1:9292 ATO_E2E_M4_RESTORE=1 npm run test:e2e --prefix frontend -- tests/e2e/m4-restore.spec.ts
```

Para repetir la medición, arrancar Next con el mismo origen usado en build y ejecutar desde `frontend/` `ATO_CHROME_PATH=/ruta/chrome node scripts/measure-public.mjs`; informes en `target/m4-quality/`. Los assets de test no se presentan como obras reales. `scripts/release-manifest.py` registra hashes del JAR, contrato, migraciones, lockfile y árboles standalone/estáticos/públicos, junto con estado Git sin commit y despliegue pendiente.

## Pendientes para producción

Actualización posterior de UI, 13 de octubre de 2026 (BK-010): la corrección inicial que validaba el UUID fue sustituida por búsqueda por nombre/correo, selección/confirmación, lista legible de accesos y revocación. Invitación y aceptación se realizan mediante enlace, sin copiar IDs/códigos. Contratos de mutación y controles de acceso anteriores conservados; nuevos endpoints privados y V9 expansiva. Ver [entrega, pruebas y límites actuales](23-cuentas-y-accesos.md). La tabla anterior conserva las evidencias de la entrega inicial M4.

Completar correo/texto/políticas del aviso, aprobar contenido/fotos reales, configurar Gmail/Drive/carpeta/credenciales, release estable de Boot, IdP y backup externo/escrow, medir restore completo y probar cache/permisos Cloudflare. RPO/RTO son objetivos aceptados; no prometer cumplimiento antes de esos ensayos. M5–M6 siguen pendientes.
