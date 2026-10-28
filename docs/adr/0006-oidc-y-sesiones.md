# 0006 — Keycloak local, sesión web y bearer API

**Estado:** Aceptado e implementado para desarrollo M0. **Fecha:** 7 de octubre de 2026. **HU:** HU-11/HU-12. **BK:** BK-005/BK-007/BK-043. **Pendiente:** P-02 productivo. [Arquitectura](../04-arquitectura-tecnica.md) · [Entrega](../16-entrega-m0.md).

## Contexto y decisión

El usuario eligió Keycloak local en Docker para desarrollar identidad y posteriormente decidió prescindir de MFA obligatorio. Spring es cliente OIDC con Authorization Code + PKCE y sesión JDBC para web; resource server separado para bearer. La cookie usa HttpOnly/SameSite=Lax y Secure fuera de dev; mutaciones web requieren CSRF. Cookie y bearer juntos se rechazan. Roles y membresías se consultan en ATO, no se conceden por claims de negocio del IdP.

MFA desactivado por defecto (`ATO_REQUIRE_MFA=false`). Roles, invitaciones y soporte requieren `auth_time` de los últimos cinco minutos; reautenticar pide contraseña con `prompt=login`/`max_age=0`. El flag true exige además evidencia MFA del IdP y un flujo apropiado. No activar obligatoriedad sin instrucción del usuario.

## Consecuencias y alternativas

Login con contraseña facilita el acceso según la preferencia del usuario. Se conservan expiración, revocación, CSRF, límites de permisos y auditoría. Passwords propios y bearer en localStorage no son necesarios. La invitación de ATO es de un uso y ligada al correo verificado; no sustituye la recuperación de cuenta en el IdP.

Proveedor productivo, operación/SMTP y gobierno de recuperación del gerente pendientes. No hay impersonación ni endpoint para resetear credenciales de GERENTE. Cambiar el archivo de importación no actualiza automáticamente un realm ya creado: se requiere operación administrada con backup.

## Verificación

Pruebas con firmas JWT reales verifican issuer/aud/exp; integración PG prueba roles, CSRF, logout, revocación, invitaciones y reautenticación. E2E usa el login real de Keycloak sin OTP, sin almacenar tokens en JavaScript. Compose se valida como configuración; por falta de daemon Docker, aquí se ejecutaron binarios aislados de Keycloak/PostgreSQL. [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html).
