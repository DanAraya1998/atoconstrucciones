# 25 — Altas y aprobación de cuentas

[Índice](README.md) · [ADR 0017](adr/0017-altas-y-aprobacion-de-cuentas.md) · [Permisos](03-roles-y-permisos.md)

Pedido autorizado el 27 de octubre de 2026: alta directa de gerencia y registro por iniciativa del usuario con revisión. HU-01/HU-04/HU-11; BK-005/BK-006/BK-010. Conserva Keycloak y permisos separados de cartera/obra; evoluciona el [selector anterior](23-cuentas-y-accesos.md). No implementa la conversión de contactos descrita en el documento 24, que conserva su propio alcance.

## Recorridos y aceptación

- **Alta directa:** Mi cuenta → Usuarios y accesos → Crear una cuenta. Nombre/correo, perfil, cliente/proveedor cuando corresponda y obras. Representación contractual explícita por obra. «Crear y habilitar» crea identidad, aprueba y asigna los accesos elegidos. Desde cartera, «Habilitar acceso» abre el mismo formulario con la ficha seleccionada.
- **Registro propio:** Ingresar → Crear cuenta. Keycloak recoge datos, verifica correo y configura contraseña. ATO muestra «Tu acceso está pendiente de aprobación», sin navegación empresarial. Gerencia revisa y pulsa «Aprobar y conceder acceso» con perfil/cartera/obras. «Consultar estado» actualiza permisos sin volver a registrarse.
- **Cuenta existente:** buscar y revisar identidad en Usuarios y accesos. «Ampliar accesos» añade perfiles y obras. Coincidencia de correo en alta devuelve conflicto y exige revisión; no fusiona identidades. Una persona puede tener varias obras/perfiles aprobados.
- **Rechazar/suspender/reactivar:** motivo, contraseña reciente, versión e idempotencia. Rechazo solo desde pendiente; login no lo revierte. Suspensión bloquea la siguiente petición y conserva datos. Reactivar recupera el estado previo. GERENTE no se modifica por estos formularios.

Solo GERENTE consulta directorios y decide accesos. El solicitante consulta `/accounts/me`; conocer otro ID no habilita su lectura. Listas paginadas con nombre/correo literal en cuerpo. Seleccionar por sí solo no concede permisos. Ampliar añade accesos; retirarlos individualmente sigue en las fichas de cartera/obra.

## Datos, estados y errores

V10 agrega `access_state` PENDING/APPROVED/REJECTED, `access_version`, `email_verified` y datos de decisión. `enabled=false` se presenta como SUSPENDED, conservando el estado previo para reactivar. La migración conserva identidades/roles/habilitación/nombres/membresías anteriores, dejando pendientes las cuentas sin roles. El nuevo registro requiere correo verificado de OIDC y crea estado PENDING; el alta directa empieza aprobada y sin verificación/contraseña, hasta completar activación en el IdP.

`account_activation` conserva PENDING/SENDING/SENT/FAILED; NONE para otras cuentas. SENT acredita aceptación por Keycloak, no llegada a una bandeja real. Cinco intentos con backoff y lease de 60 segundos; gerencia puede reencolar. Un fallo de correo conserva la aprobación y un estado recuperable. En restore, mantener el worker apagado hasta revisar cola y posibles duplicados.

[OpenAPI 0.4.2](openapi/m4.json): `/accounts`, `/accounts/management-search`, `/accounts/capabilities`, `/accounts/me`, `/accounts/{id}` y acciones `/approve|reject|suspend|resume|activation`. API expansiva, contratos anteriores conservados. Cookie+CSRF o bearer; decisiones con contraseña ≤5 minutos. Versiones de cuenta/cartera/obras: 412 requiere revisar datos actualizados. 409 señala cuenta existente/intención reutilizada/estado incompatible; 502/503 permite reintentar integración/configuración. Validación fallida revierte todos los permisos locales.

## Configuración local

1. Respaldar DB y configuración privada del IdP; reiniciar backend para V10 y reconstruir/recargar frontend.
2. Completar `ATO_IDP_SMTP_*` en el archivo privado de entorno para verificación/recuperación. Gmail requiere su credencial correspondiente, independiente del contacto público. Sin valores en Git/chat.
3. Inspeccionar `python3 scripts/configure-account-onboarding.py`: muestra alcance sin mutar. Ejecutar `python3 scripts/configure-account-onboarding.py --apply` sobre Keycloak local. Guarda copia privada en `.m0-local/`, habilita registro/verificación y atributos técnicos privados; crea cliente de servicio separado y agrega `/login` a los retornos web permitidos. Conserva usuarios/clientes/URLs previas, sin reimportación ni cambio de contraseñas existentes.
4. El comando guarda cliente/secreto/habilitación en el archivo privado mode 600. Reiniciar backend para cargarlo. La UI muestra indisponibilidad si el alta no está configurada.
5. Probar con destinatarios autorizados ambos recorridos y recuperación de contraseña del IdP.

El configurador solo admite HTTP de loopback. Producción necesita estos controles en su IdP HTTPS, permisos de servicio y secretos custodiados, más los [gates M4](22-operacion-m4.md). El realm de importación actualizado solo afecta instalaciones nuevas; uno existente requiere el configurador. SMTP/entrega productiva siguen pendientes hasta tener credenciales y ensayo real.

## Verificación

Pruebas: espera sin permisos, identidad propia/ajena, ADMIN denegado, CSRF, contraseña reciente, protección GERENTE, colisiones sin fusión, idempotencia, rollback por obra ajena/versiones, aprobación concurrente, rechazo persistente, suspensión/reactivación y cola fallida/reintentada. Protocolo: correlación tras éxito remoto, ausencia de contraseñas y rechazo de identidades sin marca ATO. Upgrade V9→V10 conserva autorizadas/desactivadas.

Ensayo opt-in `ATO_E2E_ONBOARDING=1`: Keycloak 26.8.0 verificado con SHA-256 oficial, realm `ato-onboarding` en 8182; SMTP capturado en 2527; backend/PostgreSQL nuevo en 9194; standalone aislado en 3302. `scripts/run-onboarding-browser.py` solo admite ese realm/origen y `.m0-local/onboarding-isolated/test.env`. `scripts/onboarding-test-smtp.py` rechaza destinatarios fuera de `example.invalid`. Sin mensajes reales ni modificación del realm/frontend habituales.

Resultados ejecutados el 27 de octubre de 2026:

| Verificación | Resultado |
|---|---|
| Maven `verify`, Java 27 y PostgreSQL 18.6 | **119 pruebas pasan de 123 casos**; cuatro ensayos externos Drive/ClamAV omitidos explícitamente. Incluye 13 casos nuevos de integración de altas, tres de protocolo Keycloak y uno V9→V10 |
| Playwright | **15 flujos pasan**: conjunto de 14 (fundamentos, cartera/invitaciones, creación de obras, errores y ambos tipos de alta) más compatibilidad de invitación/vinculación/revocación con Keycloak real |
| OIDC/SMTP reales locales | Registro → correo → contraseña → pendiente → aprobación y obra; alta directa → activación → contraseña → obra; suspensión bloquea sesión existente. Credenciales permanecen en Keycloak |
| Móvil, teclado y Axe | Usuarios/alta a 390 px sin desbordamiento; aprobación/alta por teclado; cero violaciones automatizadas WCAG 2/2.1/2.2 AA en las vistas ensayadas |
| Frontend | ESLint, TypeScript estricto, transporte Node y build standalone **Next 16.4.3** correctos, con origen 9194 fijado al compilar y arrancar |
| Seguridad de dependencias | Next 16.4.3, fast-uri 3.1.9 y Zod 4.6.7; `npm audit` reporta **0 vulnerabilidades conocidas** al verificarse. Sin bibliotecas nuevas |
| Migración/restore | V9→V10 conserva identidad, roles, habilitación y membresías; copia AES-GCM y restore a DB nueva: **62 tablas, 10 migraciones**, conteos/hashes de todas las filas idénticos; tres cuentas, tres roles, cartera/obra, dos accesos de obra, suspensión y activación conservados. **1,872 s**, excluyendo arranque/smoke, objetos e IdP |
| Contratos/documentación | OpenAPI expansiva contra la versión anterior, cliente regenerado, enlaces/secretos y tres pruebas del checker correctos |

La entrega produce JAR y standalone locales. CI remota, SMTP/Gmail real, configuración del IdP productivo, objetos/IdP en este restore y publicación no se ejecutaron. La copia de DB no demuestra RPO/RTO productivos. Los correos de este ensayo se capturaron localmente, con destinatarios sintéticos.

## Reproducir el ensayo aislado

Usar una distribución independiente de Keycloak 26.8.0 verificada con su SHA-256 oficial. Con los fixtures locales ya generados por `scripts/dev-env.py`, `python3 scripts/prepare-onboarding-test.py --keycloak-home /ruta/keycloak-26.8.0` prepara el realm y el frontend de pruebas sin sobrescribir un entorno aislado existente. `--frontend-only` actualiza únicamente ese frontend. La distribución debe ser exclusiva del ensayo, sin otros realms/datos.

Arrancar Keycloak en 8182 con `start-dev --http-host=127.0.0.1 --http-port=8182 --import-realm`, tomando `KC_BOOTSTRAP_ADMIN_USERNAME=local-admin` y el password de `ATO_KEYCLOAK_ADMIN_PASSWORD` del archivo privado de pruebas, sin imprimirlo. En terminales independientes:

```bash
python3 scripts/onboarding-test-smtp.py
python3 scripts/configure-account-onboarding.py --env-file .m0-local/onboarding-isolated/test.env --apply
./mvnw --batch-mode --no-transfer-progress test-compile dependency:build-classpath -Dmdep.outputFile=target/m2-test-classpath.txt
python3 scripts/run-onboarding-browser.py
npm run build --prefix target/onboarding-frontend
```

Con los servicios disponibles, ejecutar desde `frontend/`:

```bash
ATO_CHROME_PATH=/ruta/chrome ./node_modules/.bin/playwright test --config playwright.onboarding.config.ts tests/e2e/onboarding-real.spec.ts tests/e2e/onboarding.spec.ts tests/e2e/membership.spec.ts tests/e2e/project-creation.spec.ts tests/e2e/foundations.spec.ts
```

El config explícito activa el ensayo de onboarding y levanta el standalone aislado en 3302. El caso de compatibilidad real se ejecuta con `ATO_E2E_ACCOUNTS=1 ATO_E2E_ACCOUNTS_ORIGIN=http://localhost:3302` y `tests/e2e/accounts-real.spec.ts`. Una vez sin escrituras/efectos externos, `python3 scripts/verify-account-restore.py --synthetic --onboarding` cifra/restaura a una base nueva y compara todas las tablas; nunca reemplaza el origen. Detener únicamente los procesos del ensayo al terminar.
