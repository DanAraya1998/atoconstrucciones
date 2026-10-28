# 14 — Despliegue y operación

[Índice](README.md) · [Arquitectura](04-arquitectura-tecnica.md) · [Seguridad](11-seguridad-y-auditoria.md) · [Calidad](12-pruebas-y-calidad.md)

Estado: M0–M4 implementados para desarrollo local. [Entrega M4](21-entrega-m4.md) y [runbook](22-operacion-m4.md) registran artifacts, pruebas y límites de exposición. M5–M6 siguen planificados.

## Topología y entornos propuestos

Hosting local con Cloudflare Tunnel confirmado en M4: Next y Spring en loopback, Keycloak productivo bajo `/idp`, PostgreSQL y Google Drive privado; scanner/convertidor con límites. El túnel todavía no está conectado. Compose sirve al desarrollo; runtime y backups externos del host requieren validación antes de producción. No hay necesidad demostrada de Kubernetes o múltiples regiones.

| Entorno | Configuración y datos | Acceso |
|---|---|---|
| Desarrollo | JDK 27, wrapper y Node 24 LTS propuestos; PG fijado y servicios locales; identidades/fixtures sintéticos | Loopback; `.env` excluido; sin credenciales de producción |
| Test/CI | DB efímera Testcontainers, IdP simulado/de pruebas, stubs de mail/Calendar y objetos de test | Secretos de CI mínimos y aislados; salida de test sin datos reales |
| Staging | Versiones/topología cercanas a producción; migraciones reales; pruebas de restore y OAuth autorizado de prueba | Cuenta/DB/carpetas Drive/claves/dominio de prueba separados; datos anonimizados |
| Producción | Imágenes inmutables, TLS, IdP y política de autenticación acordados (MFA opcional), DB/objetos privados, política de backup/retención y scanner actualizado | Exposición solo proxy/API pública autorizada; DB/Actuator/scanner en red privada |

Ningún entorno productivo está desplegado. Compose de desarrollo fija PostgreSQL 18.6, Keycloak 26.8.0 y ClamAV 1.4.6 con secretos externos; esas etiquetas no sustituyen la validación de runtime/digests para producción.

## Configuración y secretos

Inventario de claves de configuración futuras, sin valores secretos: `SPRING_PROFILES_ACTIVE`, datasource URL/usuario/secret ref, `OIDC_ISSUER_URI`, client ID/secret ref, origins permitidos, callback URLs, session policy, `ATO_DRIVE_FOLDER_ID`, `ATO_DRIVE_CREDENTIALS_FILE`, `ATO_DRIVE_SCOPE`, acceso Drive con OAuth de usuario o cuenta de servicio para unidad compartida, key refs de cifrado, scanner endpoint/límites, mail sender/credentials ref, OAuth Google client refs y quotas, tamaño de upload, retención de temporales/auditoría, jobs leases y límite de concurrencia.

Nada sensible usa `NEXT_PUBLIC_*`; frontend recibe solo origen API/público y valores no secretos. Config validada al arrancar: production falla si faltan secretos, TLS/origins o políticas necesarias; no fallback a configuración insegura. Perfiles de logging limitan valores financieros/personales. Separar secrets de app, migraciones, backups y operador; rotación y recuperación de claves ensayadas, no solo dump de DB.

## Contenedores y build

Backend JAR Maven, imagen multi-stage con JDK/JRE **27** de distribución seleccionada y soporte evaluado; frontend build standalone compatible con Next fijado. Imágenes/PG/scanner por tag exacto y digest; no `latest`. Usuario no root, filesystem read-only cuando posible, tmp/volúmenes mínimos, límites CPU/memoria/tiempo y health checks. No empaquetar `.git`, PDFs del usuario o secretos en imagen.

Antes de elegir imagen de runtime: resolver P-01, comprobar disponibilidad/licencia/ciclo de distribución JDK 27, Maven/plugins/ORM/driver/Flyway/PDF y scanner bajo arquitectura del host. No extrapolar soporte de Spring desde `java -version` ni sustituir JDK para “que pase CI” sin acuerdo.

## Pipeline propuesto

PR: comprobación Markdown/enlaces/trazabilidad, formato/lint/typecheck, backend tests relevantes+integración PG, frontend build/test, OpenAPI lint/diff, SBOM/scan de dependencias/licencias, scan de secretos y E2E/suites sensibles. No usar un workflow existente que no fue encontrado: se crea en BK-004.

Release: construir una sola vez con lockfiles/wrapper, etiquetar artifacts/digests/commit, desplegar staging, ejecutar migration validate/update y smoke de roles/operación, verificar readiness y restauración correspondiente. Producción usa el mismo artifact; aprobación de despliegue es final sobre una release revisable cuando se solicite publicar, no parte de esta planificación.

No ejecutar Flyway simultáneamente desde varias réplicas por conveniencia: job/cuenta de migración controlados y exclusión de deploy, con lock Flyway. Backups previos a cambios riesgosos; registrar versión anterior/nueva. Expand→backfill→validate→contract en releases separados cuando afecte compatibilidad; app previas/nuevas conviven solo si esquema/contrato lo permite.

Rollback de app usa artifact anterior compatible; no deshacer hechos financieros ni editar migración aplicada. Si schema no permite volver, forward-fix o restauración ensayada con ventana/corte explícitos. Jobs outbox llevan versión de payload compatible para que un rollback no reenvíe invitaciones o duplique movimientos.

## Objetos y trabajos

Configuración privada por defecto, policies mínimas para app/scanner/backup, TLS, versionado de objetos si proveedor lo soporta, política de cuarentena/temporales y variantes públicas separadas. Drive no proporciona las mismas garantías de un bucket con Object Lock: BK-011 verifica revisiones fijadas, integridad, permisos heredados y acceso en la cuenta elegida. No afirmar retención inmutable frente a administradores.

Jobs llevan estado, attempts, nextAttemptAt, lease y timeout; backoff+jitter, dead-letter/revisión después de intentos configurables. Se recuperan leases vencidos tras caída sin ejecutar dos efectos; dedupe permanente de hechos/mappings externos. No dar red abierta a parser/conversor; scanner falla cerrado. Mail/Calendar no bloquean una transacción financiera.

## Backups, recuperación y retención

Objetivos P-10 **aceptados en M4**: RPO ≤24 h y RTO ≤4 h. Su cumplimiento productivo requiere ensayo del host/cuenta y copias externas; no deben presentarse como SLA demostrado. Retención técnica candidata: 7 copias diarias/4 semanales/12 mensuales, revisable por costo/política/obligaciones; no impone conservación legal de todos los documentos.

Respaldar PostgreSQL completo por ámbito, objetos/versiones, manifiesto de referencias/hash y configuración de claves/IdP necesario para recuperar. Claves no viajan en texto plano junto a backup; conservar procedimiento/secret refs y versiones protegidas. Para coherencia DB↔objetos, originals inmutables, retención de versiones que cubra la ventana DB y manifiesto; no eliminar objeto referenciado por un punto restaurable. Copia cifrada externa con permisos separados/inmutabilidad si disponible; limitar quién puede restaurar finanzas personales.

Runbook de restore:

1. Definir incidente, corte y pérdida permitida; pausar escrituras/jobs/integraciones.
2. Restaurar DB/versiones y objetos a entorno aislado; recuperar claves necesarias mediante procedimiento autorizado.
3. Verificar migraciones/checksums/FKs, fórmulas/saldos, hash/links de objetos, permisos y snapshots. Ejecutar T-RESTORE sobre registros conocidos sintéticos o auditoría autorizada.
4. Reconstruir sesión/revocación segura; no reutilizar indiscriminadamente tokens comprometidos. Suspender efectos externos hasta comparar mappings/outbox con estado externo.
5. Medir RPO/RTO logrados, revisar colas/idempotencia, reabrir tráfico y observar; registrar el incidente/correcciones sin borrar evidencia.

Ensayar antes de MVP, después de cambio importante de esquema/storage y periódicamente (propuesta trimestral). Un dump exitoso no demuestra recuperación. Retención de evidencia fiscal/contratos/auditoría/personal/fotos se decide por clase y holds en BK-013; no purge automático legalmente supuesto.

## Observabilidad y soporte

Actuator/Micrometer gestionado por Boot, métricas sin dimensiones de alta cardinalidad ni payload sensible: latencias/errores, conexiones DB, backlog/edad outbox, cuarentena, fallos de parser, cuotas Calendar/mail, capacidad disco/objetos y resultado/edad backups. Logs estructurados con traceId; dashboards técnicos no presentan valores financieros reales.

Alertas propuestas: error de comandos críticos, DB no disponible, auditoría sin persistir, scanner detenido, oldest job fuera de objetivo, copia vencida, expiración token/canal Calendar y límite de capacidad. Thresholds se ajustan con staging/uso; responsable operacional por definir. Runbooks por cada alerta incluyen diagnóstico, acción reversible, escalamiento y verificación. ADMIN opera soporte técnico bajo RN-A08; lectura de backups/sensibles usa autorización separada.

Mantenimiento: vigilar avisos de seguridad y licencias, patches probados en staging, updates de firmas scanner, crecimiento/indexes y limpieza de temporales. Planificar actualización de JDK 27/Boot sin prometer ciclo LTS no verificado; cambios de versión o proveedor documentados en ADR. [Puertas de release](12-pruebas-y-calidad.md) y BK-029 definen evidencia requerida antes de puesta en producción.

## Entorno M1 y recuperación

Perfil Compose `m1`: ClamAV LTS 1.4.6 solo en loopback. Google Drive se conecta por API con credenciales de servidor y carpeta de pruebas autorizada; no hay emulador S3 en Compose. Las pruebas locales del protocolo Drive no requieren cuenta Google. No eliminar los volúmenes o datos S3 anteriores: referencias existentes requieren migración verificada. Configuración en [.env.example](../.env.example) y [18](18-almacenamiento-google-drive.md).

Antes de producción: confirmar cuenta/carpeta/custodios, credenciales y refresh/revocación, scopes mínimos, cuota, revisión fijada, aislamiento de permisos, política documental y restore PostgreSQL+Drive+credenciales. Las plantillas S3 de infra son históricas y no se aplican. La primera CI remota y E2E empresarial con una cuenta Drive de pruebas están pendientes; el workflow común verifica protocolo HTTP local, PostgreSQL, ClamAV y OIDC sin secretos Google.

## Estado operativo M4

El usuario eligió hosting local con Cloudflare Tunnel, `atoconstrucciones.com` y Gmail SMTP; RPO ≤24 h / RTO ≤4 h quedaron **confirmados como objetivos**, sin evidencia de cumplimiento productivo. [Runbook M4](22-operacion-m4.md) entrega configuración concreta, JAR/standalone/manifiesto, copia cifrada/escrow, restore aislado y alertas técnicas. No se instaló túnel ni se publicaron servicios. CI incluye M4; ejecución remota pendiente. Boot estable/IdP, cuenta Drive/ACL/restore real, correo del aviso, Gmail, backups externos y runtime del host conservan gates antes de exposición. Este estado sustituye las referencias históricas a hosting sin seleccionar, Compose `latest` o capacidades M2–M4 solo planificadas de las secciones iniciales.
