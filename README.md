# ATO Construcciones

M0–M4 implementados para desarrollo: Java 27, Spring Boot 4.2.0-M2, PostgreSQL 18.6, Keycloak 26.8.0 y frontend Next.js 16.4 / React 19.3. M1 añade cartera, obras, contratos, extras y evidencias privadas; M2 añade cobros, costos, pagos/anticipos, indicadores y XML 4.4; M3 añade cronograma, pesos/avance verificado y portal cliente; M4 añade publicación y contactos. [Plan maestro](docs/README.md) · [Entrega M0](docs/16-entrega-m0.md) · [Entrega y límites M1](docs/17-entrega-m1.md) · [Entrega y límites M2](docs/19-entrega-m2.md) · [Entrega y límites M3](docs/20-entrega-m3.md) · [Entrega y límites M4](docs/21-entrega-m4.md).

## Desarrollo local

Requiere JDK 27, Docker con Compose y Node 24.21.0 (`frontend/.nvmrc`). Los puertos 54329, 8180, 8080 y 3000 deben estar libres; M1 usa además 9090 y 3310.

```bash
python3 scripts/dev-env.py
docker compose up -d --wait postgres
docker compose up -d keycloak
```

Esperar a que `http://localhost:8180/realms/ato/.well-known/openid-configuration` responda. En terminales separadas:

```bash
python3 scripts/run-backend.py
```

```bash
npm ci --prefix frontend
npm run dev --prefix frontend
```

Abrir **http://localhost:3000**. Las contraseñas sintéticas se generan en `.m0-local/accounts.txt`; no se guardan en Git. La cuenta `gerente` recibe GERENTE únicamente en perfil `dev` y solo si no hay otra gerente. Las demás cuentas quedan pendientes hasta aprobación de gerencia. En **Usuarios y accesos** se pueden crear cuentas directamente o aprobar solicitudes de autorregistro, asignando perfil/cartera/obras en una operación. El IdP debe tener el cliente de servicio y SMTP configurados; [recorrido, configuración y límites](docs/25-altas-y-aprobacion-de-cuentas.md). Los enlaces de invitación anteriores siguen admitidos.

El acceso usa [tema ATO para Keycloak](docs/28-login-con-identidad-ato.md) y `/login` abre directamente el formulario. Para un realm existente: `docker compose up -d keycloak`, esperar a que responda y ejecutar `python3 scripts/configure-keycloak-theme.py --apply`. No se borran volúmenes ni se cambia el emisor. Registro y recuperación conservan sus acciones nativas; SMTP productivo y dominio sin puerto siguen sujetos a configuración/ensayo.

**MFA está desactivado**, según la decisión del usuario. Cambios de roles, invitaciones, soporte y acciones sensibles de M1/M2 requieren un login con contraseña de los últimos cinco minutos. Reautenticar vuelve a pedir la contraseña. Un grant de soporte se limita a un recurso sintético, solo lectura y máximo 30 minutos; no concede GERENTE. La sucesión o recuperación del gerente no tiene un endpoint ordinario.

`ATO_REQUIRE_MFA=true` permite exigir MFA en acciones sensibles en el futuro, junto con el correspondiente flujo del IdP. `python3 scripts/dev-env.py --refresh-realm` actualiza el archivo de importación local preservando contraseñas; **Keycloak no actualiza automáticamente un realm existente** con `--import-realm`. Aplicar cambios sobre un realm existente requiere una importación administrada y backup. No limpiar volúmenes para actualizar configuración.

Los secretos y datos de desarrollo están en `.env`, `.m0-local/` y volúmenes propios `ato-m0`; Compose publica servicios solo en loopback. Producción no usa `dev`, exige HTTPS, secretos externos y cookie Secure; bloquea bootstrap y debug. No hay despliegue productivo ni correo/recuperación SMTP configurados en esta entrega.

Si Spring muestra `Connection to 127.0.0.1:54329 refused`, comprobar que el PostgreSQL de Compose esté iniciado con `docker compose up -d --wait postgres`. Si Docker devuelve permiso denegado sobre `/var/run/docker.sock`, ejecutar los comandos de Compose con `sudo` desde una terminal con acceso administrativo. `pg_isready -h 127.0.0.1 -p 54329 -U ato -d ato` debe responder `accepting connections`. Iniciar también Keycloak antes de Spring. PostgreSQL del sistema en 5432 es una instancia independiente; la configuración de este proyecto conserva el puerto Docker 54329. Maven y el harness usan bases temporales para sus pruebas; el arranque habitual requiere los servicios de Compose.

### Evidencias M1 en Google Drive

Google Drive sustituye S3 por decisión del cliente. Configuración de cuenta/carpeta/credenciales pendiente: [pasos y límites](docs/18-almacenamiento-google-drive.md). Hasta conectarlo, conservar `ATO_STORAGE_ENABLED=false` para ver cartera, obras y privacidad; no hay cargas funcionales sin Drive autorizado.

```bash
docker compose --profile m1 up -d clamav
docker compose exec -T clamav clamdscan --ping=1
```

Esperar a que FreshClam descargue firmas y ClamAV responda. Para habilitar cargas, configurar en `.env` `ATO_DRIVE_FOLDER_ID`, `ATO_DRIVE_CREDENTIALS_FILE` y `ATO_STORAGE_ENABLED=true`, con credenciales de servidor autorizadas y carpeta privada. Reiniciar Spring. OAuth de usuario sirve para Mi unidad; una cuenta de servicio requiere una unidad compartida Workspace. La selección/consentimiento de carpeta y la cuenta real siguen pendientes, no se conecta automáticamente al Drive del cliente.

GERENTE registra contratante y membresías; una obra necesita contrato formalizado, representante activo y ubicación para activarse. Los archivos quedan en cuarentena hasta verificar tipo, hash y antivirus. La descarga revalida permisos y revisión fijada. No eliminar datos/volúmenes S3 anteriores: V5 los identifica para migración verificada, sin reinterpretar sus referencias. [ADR 0011](docs/adr/0011-google-drive-evidencias.md).

## Verificación

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
python3 scripts/check_project.py
python3 scripts/test_checks.py
npm run api:generate --prefix frontend
npm run lint --prefix frontend
npm run typecheck --prefix frontend
npm run test --prefix frontend
npm run build --prefix frontend
```

Los tests backend arrancan PostgreSQL 18.6 nativo en un directorio/puerto temporal, sin Docker ni H2. El usuario que los ejecuta no debe ser root. Node, Maven y los binarios de prueba requieren descarga inicial; después se aprovechan las cachés.

Con ClamAV local activo, ejecutar su ensayo real junto a las pruebas HTTP de Drive y PostgreSQL:

```bash
ATO_M1_INFRA=1 ./mvnw --batch-mode --no-transfer-progress clean verify
```

Sin `ATO_M1_INFRA`, se omite el ensayo ClamAV. Las tres pruebas de conexión Google real se ejecutan solo con `ATO_DRIVE_LIVE_TESTS=1` y credenciales/carpeta de pruebas autorizadas; nunca sobre el Drive operativo. Las suites locales verifican negocio, permisos, concurrencia, upgrades y protocolo Drive con un fixture HTTP.

```bash
cd frontend
npx playwright install chromium
ATO_E2E_KEYCLOAK=1 npm run test:e2e
```

Las pruebas reales necesitan Spring/PostgreSQL/Keycloak locales activos y el realm sin MFA obligatorio; Para añadir `ATO_E2E_M1=1`, conectar el backend a Google Drive de pruebas y mantener ClamAV activo. El flujo M1 crea cartera/obra, adjunta evidencia, formaliza contrato, cotiza/acepta/aprueba un extra y revoca acceso; comprueba móvil y teclado. Las otras tres pruebas cubren móvil/teclado, hidratación SVAR y transporte del portal con fixtures. Sin las banderas correspondientes se omiten explícitamente los flujos reales. `ATO_CHROME_PATH` permite seleccionar un Chromium instalado. No compartir los fixtures ni resultados de sesiones.

## Contratos y estructura

Backend Maven en raíz, módulos `identity`, `audit`, `shared`, `foundation`, `parties`, `projects`, `contracts`, `documents`, `finance`, `costs`, `treasury`, `reporting` y `scheduling`; frontend en `frontend/`. [OpenAPI M0–M4](docs/openapi/m4.json) genera `frontend/src/lib/api/schema.d.ts`; [M0](docs/openapi/m0.json) permanece como baseline. `/api/v1/openapi.json` exige identidad activa. APIs de sesión cookie y bearer se distinguen; mezclar bearer con cookie se rechaza. Las mutaciones cookie exigen CSRF.

La demostración M0 conserva su recurso ficticio. M1 opera sobre tablas empresariales independientes, importes exactos en CRC y cambios auditados/idempotentes con ETag. M2 implementa operación financiera en `/obras/{id}` y `/finanzas`; M3 añade planificación/portal cliente; M4 implementado para desarrollo local; M5–M6 siguen pendientes. Migraciones Flyway versionadas, `ddl-auto=validate`, auditoría protegida contra UPDATE/DELETE/TRUNCATE; no editar migraciones ya aplicadas.

### Ensayo financiero M2 aislado

[Pasos reproducibles](docs/19-entrega-m2.md) preparan un backend de test en 9192, PostgreSQL temporal y Keycloak real. `ATO_E2E_M2=1` activa los dos flujos financieros/XML. Archivos y antivirus son controlados exclusivamente en test; no reemplazan Drive en la aplicación. `scripts/verify-m2-restore.py` verifica la copia de esa DB, sin tocar la DB de desarrollo habitual.

## Planificación y portal cliente M3

Actualización 27/10/2026: [reprogramación y proveedores](docs/26-reprogramacion-y-proveedores.md). **Reprogramar** mueve inicio/duración/fechas con historial; el Gantt adapta altura a tareas y ofrece **Ampliar Gantt**. Proveedores se registran sin obra/cuenta, con categoría principal y filtro. Reiniciar backend para V11 y recargar/reconstruir frontend.

GERENTE/OPERATIONS planifican en `/obras/{id}` mediante lista/formulario o Gantt. GERENTE aprueba pesos físicos y verifica mediciones; proveedores reportan tareas asignadas. Clientes usan `/cliente` para sus obras, cobros, extras y documentos. Sin cobertura aprobada el avance aparece pendiente de definición.

[Ensayo aislado y resultados M3](docs/20-entrega-m3.md) reutilizan el harness con `python3 scripts/run-m2-browser.py --milestone m3`, `ATO_E2E_M3=1` y `python3 scripts/verify-m2-restore.py --milestone m3`. Mantener Drive real/configuración productiva y M5–M6 como pendientes; M4 local se documenta a continuación.

## Sitio público y entrega M4

Rediseño del 27/10/2026: [landing editorial](docs/27-rediseno-publico-y-lorem-ipsum.md), servicios, galería visual y páginas públicas con lorem ipsum autorizado cuando no hay textos aprobados. Imágenes conceptuales identificadas como muestra; las publicaciones reales mantienen sus controles. Recargar el frontend en desarrollo o reconstruir/reiniciar standalone; no hay migración ni cambio backend.

[Entrega M4](docs/21-entrega-m4.md) y [operación](docs/22-operacion-m4.md): páginas públicas y panel `/publicacion`, textos revisados, galería con consentimiento, contactos durables/Gmail, release y recuperación local. Completar aviso antes de recibir datos reales. Cloudflare Tunnel/Gmail están preparados; no están conectados a cuentas reales ni desplegados. El entorno `ATO_BACKEND_ORIGIN` debe coincidir en build y arranque Next.

Actualización de cuentas del 13 de octubre: [V9 y flujo sin UUID manuales](docs/23-cuentas-y-accesos.md). Reiniciar backend para aplicar la migración y cargar el directorio; recargar el frontend de desarrollo o reconstruir/reiniciar standalone. `ATO_E2E_ACCOUNTS=1` habilita el recorrido real de invitación/vinculación/revocación contra el harness aislado; usar exclusivamente datos sintéticos. El envío automático de invitaciones por Gmail sigue pendiente; el flujo actual comparte un enlace.
