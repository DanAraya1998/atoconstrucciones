# 22 — Operación y release M4

Presentación pública 27/10/2026: el optimizador Next permite el logo y los tres paths estáticos exactos de conceptos WebP documentados en [27](27-rediseno-publico-y-lorem-ipsum.md). Mantiene bloqueadas rutas `/api`, originales privados y URLs remotas. El artefacto de ensayo de landing apunta al fixture 9355; reconstruir el frontend con el origen habitual antes de usarlo fuera de ese ensayo. Lorem ipsum/conceptos no acreditan contenido empresarial real ni aceptación productiva.

Actualización 27/10/2026: [operación de cuentas V10](25-altas-y-aprobacion-de-cuentas.md). Configurar cliente de servicio Keycloak separado y SMTP de verificación/recuperación antes de habilitar altas reales. En restore, mantener `ATO_ACCOUNT_ACTIVATION_WORKER_ENABLED=false`, revisar estados/leases y posibles duplicados antes de activarlo. La migración no reactiva cuentas suspendidas; exige conservar issuer/subject y permisos restaurados. Next.js actual se fija en 16.4.3 por avisos de seguridad confirmados; las evidencias M4 siguientes son históricas.

[Índice](README.md) · [Entrega](21-entrega-m4.md) · [ADR 0014](adr/0014-publicacion-contacto-operacion-m4.md) · [Operación canónica](14-despliegue-y-operacion.md)

## Decisiones y alcance

Confirmados: hosting local + Cloudflare Tunnel, `atoconstrucciones.com`, Gmail SMTP, RPO ≤24 h y RTO ≤4 h. El reloj del ensayo local no demuestra recuperación de una cuenta Google/Cloudflare, del host físico ni un SLA. Cada copia diaria debe salir del host a un destino cifrado independiente; para sostener RPO 24 h, alertar antes de que la última copia verificada alcance 24 h. Sin copia externa de objetos, DB e IdP/credenciales no hay aceptación productiva.

## Configuración revisable del host

[Entorno](../infra/production.env.example), [túnel](../infra/cloudflared.config.example.yml), [Compose de desarrollo](../compose.yaml). No se instalaron servicios del sistema ni se creó DNS/túnel. Usar usuario de servicio sin privilegios, directorios privados y secrets fuera del repositorio. Spring y Next escuchan en loopback; mantener DB/ClamAV/Actuator privados. El entorno Next de **build y arranque** debe apuntar al mismo `ATO_BACKEND_ORIGIN`; las rewrites se fijan al construir, mientras SSR lee la configuración de ejecución.

El IdP productivo requiere base externa, credenciales/clientes propios y realm revisado; no publicar el `start-dev` ni las cuentas sintéticas. Para el mismo dominio, configurar Keycloak con ruta `/idp`, hostname `https://atoconstrucciones.com/idp`, HTTP solo loopback y proxy confiable. Validar issuer, audiencia, callback HTTPS, cookies Secure y logout desde staging. No conectar el túnel mientras estos controles sigan pendientes.

El hostname raíz entra a Next; `/idp` al IdP. Excluir de cache Cloudflare/proxy `/api/*`, `/auth/*`, `/oauth2/*`, `/login/*`, `/idp/*`, todas las páginas privadas y las páginas públicas dinámicas. No activar Cache Everything sobre estos paths. Respetar `Cache-Control: no-store`; el optimizador Next rechaza imágenes ajenas al logo. Validar con un consentimiento retirado y con 401/403 que el edge nunca entrega contenido previo. `cloudflared tunnel ingress validate` se ejecutará después de reemplazar el UUID/credencial; validar DNS, certificado y métricas del túnel en la cuenta autorizada.

Gmail: `ATO_GMAIL_USERNAME`, `ATO_GMAIL_APP_PASSWORD`, `ATO_MAIL_RECIPIENT`, `ATO_MAIL_SENDER_CONFIRMED=true`, luego `ATO_MAIL_ENABLED=true`. SMTP se fija a `smtp.gmail.com:587`, autenticación y STARTTLS requeridos, verificación de hostname, timeouts de 5 segundos. Google puede requerir verificación en dos pasos y contraseña de aplicación **en Gmail**; esto no cambia MFA del portal. Cuenta, remitente, destinatario, restricciones de Google, SPF/DKIM/DMARC aplicables y entrega de prueba siguen pendientes. No pegar contraseñas en chat ni Git. Aviso: completar correo real, responsable definido en la sesión, texto/finalidad/retención, y aprobar en `/privacidad`. Hasta entonces no usar datos de visitantes reales.

## Build, migración y rollback

```bash
./mvnw --batch-mode --no-transfer-progress verify
npm run api:generate --prefix frontend
npm run lint --prefix frontend
npm run typecheck --prefix frontend
npm run test --prefix frontend
npm run build --prefix frontend
python3 scripts/check_project.py --baseline docs/openapi/m3.json
python3 scripts/release-manifest.py
```

Guardar el JAR, el directorio standalone Next y estáticos, lockfile, manifiesto SHA-256, reportes y revisión identificable. `target/m4-release-manifest.json` indica si hay trabajo sin commit; no atribuirlo falsamente al HEAD anterior. No se construyeron imágenes ni se comprobaron digests de runtime: fijar/ensayar imágenes o servicios del host antes de release productiva. Se entrega el JAR y standalone concretos, sin cambiar Java ni BOM preview.

V8 es expansiva y conserva el esquema de M3. Ejecutar migración una vez, con cuenta controlada, backup previo y `ddl-auto=validate`; nunca `clean`. Ensayo V7→V8 conserva contrato, cronograma y rol ADMIN. Rollback de aplicación a un artifact M3 compatible puede dejar tablas V8 sin consumidores; pausar worker de correo y túnel durante el cambio. No eliminar V8 ni hechos financieros. Si se cambia la estructura posteriormente, volver a comprobar compatibilidad; no dar por probado un binario M3 que no se conserva en esta entrega.

## Copia cifrada con Drive privado

Pausar aplicación/escrituras/worker e integraciones; generar `pg_dump -Fc` consistente con las referencias Drive. Guardar el dump temporal en carpeta mode 700, no en el repositorio. Custodiar una clave aleatoria de 32 bytes en hex, archivo mode 600, en un vault/escrow independiente. Usar el CLI dentro del JAR:

```bash
java -Xmx1g -Dloader.main=com.daraya.atoconstrucciones.documents.RecoveryTool \
  -cp target/atoconstrucciones-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  export /ruta/privada/database.dump /ruta/nueva/copia /ruta/escrow/backup.key
```

El proceso necesita `ATO_DB_URL/USER/PASSWORD`, credenciales/carpeta Drive y `ATO_BACKUP_WRITES_PAUSED=true`. No inferir pausa de ese flag: el operador debe detener servicios y comprobar ausencia de escrituras. Exporta originales con versión y variantes, verifica tamaño/hash y ACL del adaptador Drive, rechaza S3 sin migración, cifra cada objeto/manifiesto/DB, y nunca copia credenciales en el bundle. Límite inicial **256 MiB por archivo**, incluido dump; medir volumen antes de usarlo. Cifra también las copias del IdP y la configuración privada con el subcomando `encrypt`, usando otra clave/custodia cuando corresponda. Verificar copia:

```bash
java -Xmx1g -Dloader.main=com.daraya.atoconstrucciones.documents.RecoveryTool \
  -cp target/atoconstrucciones-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  verify /ruta/copia - /ruta/escrow/backup.key
```

La verificación autentica GCM y hashes; no sustituye `pg_restore` ni prueba ACL de una cuenta recuperada. Conservar el dump temporal solo según política aprobada; no dejar plaintext en backup remoto. No configurar eliminación automática hasta aprobar retención.

## Restauración y verificación

1. Registrar incidente/corte, congelar escrituras y suspender túnel/SMTP. Recuperar clave e IdP/credenciales mediante custodia separada; revocar secretos comprometidos.
2. Autenticar bundle, descifrar dump a carpeta privada nueva y restaurar con `pg_restore --exit-on-error` a una DB **nueva**, sin tocar la existente. Comprobar Flyway, FKs, snapshots, saldos y hashes de todas las tablas.
3. Verificar originales y variantes recuperados contra manifiesto. Si las revisiones Drive originales existen, comprobar lectura/ACL privada. Si la cuenta/IDs se perdieron, cargar en carpeta nueva privada y preparar mapa viejo→nuevo; revisar actualización de referencias en DB restaurada por operador, sin desactivar invariantes en operación normal. Ese escenario exige ensayo real y no está automatizado aquí.
4. Invalidar Spring Sessions restauradas, revisar cuentas/roles/membresías y el issuer recuperado. Probar cliente propio/ajeno, proveedor, ADMIN y gerente; retirar consentimiento y comprobar que asset/ficha devuelven 404.
5. Revisar notificaciones pendientes: SMTP pudo aceptar un mensaje antes de una caída. Reencolar con decisión explícita y aceptar posible duplicado de notificación; nunca duplicar un contacto o hecho financiero.
6. Medir tiempo/pérdida reales contra 24 h/4 h. Arrancar aplicación sin efectos externos; smoke público/privado, health, luego habilitar SMTP y túnel únicamente sobre release aprobada.

El ensayo reproducible M4 usa `scripts/verify-m2-restore.py --milestone m4`: copia DB completa/ACL y archivos sintéticos, AES-GCM, clave externa y hashes; conserva DB de destino y carpeta nueva. Archivo `target/m4-restored-environment.json` identifica el destino; no contiene credenciales. La DB origen y los originales no se eliminan.

## Health, alertas y gates finales

Readiness `/actuator/health/readiness` y liveness son locales. `/api/v1/operations/status` exige GERENTE o ADMIN; entrega solo conteos de jobs DEAD, notificaciones DEAD y escaneos retrasados, sin datos personales/financieros. Un caso de prueba fuerza DEAD y demuestra alerta; roles de cliente no acceden. Monitor del host debe alertar por health, última copia, caída de túnel, espacio, firmas ClamAV y antigüedad de cola. El envío automático de esas alertas a un destinatario real no está configurado.

Antes de exposición: Boot estable revalidado, proveedor/realm productivos, Drive y antivirus reales con restore/ACL, correo real del aviso/políticas aprobadas, contenido/fotos aprobados, Gmail probado, backup externo/IdP/key escrow restaurados, permisos del edge probados, cuentas sintéticas excluidas y release identificada. La autorización para implementar M4 no publica esta release; la aprobación final de despliegue corresponde a ese resultado concreto y sus pendientes resueltos.
