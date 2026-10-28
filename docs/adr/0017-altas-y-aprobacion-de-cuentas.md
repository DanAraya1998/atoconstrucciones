# ADR 0017 — Alta directa y registro propio con aprobación

- Estado: aceptado para implementación local por el pedido del usuario del 27 de octubre de 2026.
- Historias/tareas: HU-01, HU-04, HU-11; BK-005, BK-006, BK-010.
- Complementa [0006](0006-oidc-y-sesiones.md) y sustituye la invitación como recorrido principal de [0015](0015-cuentas-y-accesos-sin-identificadores-manuales.md).

## Decisión

Conservar Keycloak/OIDC, contraseña reciente y permisos por recurso. Dos entradas a una misma cuenta: GERENTE crea una identidad nueva y aprueba perfil/cartera/obras en una operación; una persona se registra y verifica su correo en Keycloak y queda PENDING en ATO hasta aprobación. Rechazar conserva la cuenta; suspender conserva estado/permisos y bloquea la siguiente petición, incluida una sesión existente. Reactivar es una decisión explícita con motivo. GERENTE/sucesión permanecen fuera del flujo corriente; nunca se concede ADMIN/GERENTE mediante autorregistro o alta operativa.

V10 agrega estado/versiones/verificación/decisión y una cola de activaciones. Identidades existentes con roles se conservan aprobadas; las que no tienen roles quedan pendientes. Ninguna cuenta desactivada se habilita por migración/login. El registro OIDC nuevo inserta PENDING; bootstrap de gerencia solo en desarrollo. La identidad sigue siendo issuer/subject; nunca correo/nombre. Un cambio verificado del perfil incrementa la versión de revisión.

Identity coordina servicios de parties/projects: rol, cartera, obras, representante explícito y auditoría se confirman en una transacción con idempotencia y versiones de los tres recursos. Perfiles: CLIENTE/PROVEEDOR/ASISTENTE_ADMINISTRATIVA. Ampliar añade accesos; las revocaciones específicas conservan sus comandos de cartera/obra.

## Aprovisionamiento y correo

Cliente de servicio Keycloak separado del cliente web, con view-users/manage-users/query-users y secreto privado del backend. El adaptador solo implementa creación nueva y activación de identidades marcadas por ATO; no expone contraseñas ajenas, impersonación ni administración de roles del IdP. Los permisos técnicos del servicio requieren custodia y no se conceden a ADMIN del portal ni al navegador.

La creación remota no participa en la transacción PostgreSQL. Una referencia determinista por gerente/clave y un hash de la intención, en atributos editables exclusivamente por administración del IdP, permiten reconciliar éxito remoto seguido de rollback/timeout local. Se consulta por referencia técnica, sin correo en URL. Colisión sin referencia/hash correctos devuelve 409 y exige revisión de identidad verificada; nunca adopta/fusiona una cuenta externa por correo. Puede quedar una identidad remota sin permisos locales si falla la transacción: reintentar la misma intención o recuperar/verificar su cuenta y aprobarla explícitamente. Sin borrado automático de identidades remotas.

La identidad directa comienza sin contraseña y sin correo verificado. La cola confirmada junto al alta solicita VERIFY_EMAIL/UPDATE_PASSWORD. Guarda estado/intentos/lease/error, nunca credenciales/enlaces. Cinco intentos con backoff y reenvío explícito. Un timeout tras aceptar correo puede duplicar el mensaje, sin duplicar cuentas/permisos. El permiso efectivo exige aprobación, habilitación, correo verificado y membresías actuales.

## Consecuencias

La invitación deja de ser el recorrido principal; sus enlaces previos siguen admitidos, sin revivir cuentas rechazadas/suspendidas. El autorregistro conserva Code/PKCE/state y utiliza prompt=create. Contraseñas únicamente en Keycloak; SMTP de verificación/recuperación independiente del contacto público. Sin dependencias nuevas, cambio de MFA ni publicación.

La [propuesta previa de conversión de contactos](0016-alta-de-clientes-y-obras-con-revision.md) conserva su documento y alcance. Esta entrega concreta los flujos de cuentas autorizados en esta sesión; la conversión de contacto en cliente/obra de ese documento no se acredita como implementada por este cambio.

## Evidencia

Durante el build se confirmaron avisos de seguridad del árbol existente. Se fija Next.js 16.4.3 (parche de la misma rama), y se actualizan únicamente las transitivas afectadas fast-uri 3.1.9 y Zod 4.6.7 dentro de sus rangos. Licencias y peers verificados en npm; sin nuevas bibliotecas ni cambio de stack/BOM. La auditoría posterior reporta cero avisos conocidos. Fuentes: [aviso de imágenes](https://github.com/vercel/next.js/security/advisories/GHSA-2464-fhr4-w9xw), [Server Actions](https://github.com/vercel/next.js/security/advisories/GHSA-p3m5-59mp-jv5r), [caché](https://github.com/vercel/next.js/security/advisories/GHSA-673q-mjm6-r5xr).

[Entrega/operación](../25-altas-y-aprobacion-de-cuentas.md), [integración](../../src/test/java/com/daraya/atoconstrucciones/AccountOnboardingIntegrationTests.java), [protocolo](../../src/test/java/com/daraya/atoconstrucciones/identity/KeycloakAccountProvisioningTests.java), [upgrade](../../src/test/java/com/daraya/atoconstrucciones/AccountOnboardingMigrationTests.java), [navegador](../../frontend/tests/e2e/onboarding-real.spec.ts). El configurador local guarda copia privada y actualiza configuración sin reimportar usuarios. SMTP/IdP productivos requieren credenciales y ensayo real; correo capturado localmente no acredita entrega Gmail.
