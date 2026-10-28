# Acceso con la identidad visual de ATO

[Índice](README.md) · [Cuentas y aprobación](25-altas-y-aprobacion-de-cuentas.md) · [Operación](22-operacion-m4.md)

Autorizado el 27/10/2026: dar al acceso la estética del proyecto y entrar directamente al formulario. HU-11, BK-005/BK-028, CA-11.7. El tema `ato` hereda `keycloak.v2` de Keycloak 26.8.0: logo original, azul/naranja, tipografía de sistema, composición de dos paneles en escritorio y formulario centrado en móvil. El concepto arquitectónico reutiliza el asset ilustrativo de la landing, identificado como tal; las copias del tema deben actualizarse juntas si se reemplaza la imagen o el logo.

## Flujo y límites

`/login` redirige a `/auth/login` y al formulario OIDC de Keycloak. Ahí están **Crear cuenta** y **¿Olvidaste tu contraseña?**; `/auth/register` conserva el ingreso directo al registro. **Volver al sitio** usa la URL base administrada del cliente web, sin retorno enviado por query string. El registro sigue pendiente hasta verificar correo y obtener aprobación de gerencia; las cuentas aprobadas conservan sus accesos. Los textos de ayuda de autenticación son funcionales, no lorem ipsum comercial.

Formularios, acciones, validación, visibilidad de contraseña, enlaces de un uso, caducidad, CSRF/estado/PKCE, idioma y scripts de sesión se heredan del IdP. Solo se extienden CSS, traducciones y `footer.ftl`. No hay formulario de contraseñas en Next, direct grant público ni copia de tokens en el frontend. MFA sigue opcional/desactivado y las acciones sensibles conservan reautenticación reciente. El cambio visual no modifica permisos, estados, OpenAPI, datos ni emisor; no requiere Flyway ni sustituye ADR 0006/0017. Al actualizar Keycloak, revisar el tema padre y repetir los ensayos.

## Aplicar en el desarrollo habitual

El [Compose](../compose.yaml) monta el [tema](../infra/keycloak/themes/ato/login/theme.properties) como solo lectura. Un realm nuevo generado con `scripts/dev-env.py` lo selecciona automáticamente. Para uno **ya existente**, desde una terminal con acceso a Docker:

```bash
docker compose up -d keycloak
python3 scripts/configure-keycloak-theme.py --apply
```

Esperar a que Keycloak responda antes del segundo comando. Si el sistema exige `sudo` para Docker, ejecutar el primero con ese permiso desde la terminal del operador. No borrar volúmenes, importar encima del realm ni regenerar contraseñas. El configurador solo acepta IdP/sitio HTTP de loopback, comprueba el tema instalado antes de mutar, guarda selección anterior y URL base en `.m0-local/theme-config-*.json` con modo 600, selecciona el tema y configura el retorno al sitio. Conserva usuarios, credenciales, callbacks, permisos y emisor. Sin `--apply` muestra el cambio previsto y no conecta al IdP.

Recargar Next en desarrollo; con standalone, reconstruir/reiniciar el frontend. El backend no cambia. Abrir `/login` en una sesión nueva. Para regresar al tema estándar:

```bash
python3 scripts/configure-keycloak-theme.py --theme keycloak.v2 --apply
```

La copia privada permite restaurar otra selección previa y su URL base desde la administración. En una instalación nativa, copiar `infra/keycloak/themes/ato` a `<keycloak>/themes/ato` y reiniciar antes de seleccionar el tema. Mantener cachés de recursos/plantillas en producción; desactivarlos solo al iterar en desarrollo mediante las opciones `spi-theme` documentadas por Keycloak.

## Dominio y puerto

El tema no oculta `localhost:8180`: es la dirección del IdP de desarrollo. La [plantilla de túnel](../infra/cloudflared.config.example.yml), el [entorno productivo](../infra/production.env.example) y el [runbook M4](22-operacion-m4.md) preparan `https://atoconstrucciones.com/idp/realms/ato`, sin puerto visible, con Keycloak configurado para ese hostname/prefijo. También debe instalarse/seleccionarse el tema y configurar la URL base del cliente al sitio HTTPS. Esta entrega no conecta DNS/túnel ni cambia el emisor.

Cambiar un emisor existente exige un ensayo de migración: la identidad local es `(issuer, subject)`, no el correo. Cambiar solo `.env` podría crear cuentas pendientes distintas y perder correspondencia con permisos previos. Conservar las identidades hasta verificar la transición; no fusionar por correo. Validar callbacks, enlaces de correo, recursos del tema, cookies y cliente de servicio de altas con la topología elegida antes de producción.

Referencias primarias: [temas](https://www.keycloak.org/ui-customization/themes), [hostname](https://www.keycloak.org/server/hostname) y [reverse proxy](https://www.keycloak.org/server/reverseproxy).

## Ensayo reproducible

Usar solo el entorno sintético de [altas/aprobación](25-altas-y-aprobacion-de-cuentas.md). La preparación nueva copia el tema a la distribución nativa de ensayo y selecciona `ato`. En un entorno aislado previo, copiar primero el directorio a su Keycloak, reiniciarlo y ejecutar:

```bash
python3 scripts/configure-keycloak-theme.py --env-file .m0-local/onboarding-isolated/test.env --apply
python3 scripts/prepare-onboarding-test.py --frontend-only
npm run build --prefix target/onboarding-frontend
```

Con IdP 8182, SMTP capturado 2527 y backend/DB temporal 9194 activos según el documento 25, desde `frontend/`:

```bash
ATO_CHROME_PATH=/ruta/chrome ./node_modules/.bin/playwright test --config playwright.onboarding.config.ts tests/e2e/theme-real.spec.ts tests/e2e/onboarding-real.spec.ts
```

El config activa el opt-in aislado y levanta el frontend 3302. Solo se capturan correos `example.invalid`. Capturas fuera de Git en `target/theme-preview/`; no compartir trazas ni URLs de acciones de correo.

## Verificación de esta entrega

Ejecutado el 27/10/2026 sobre Keycloak 26.8.0, PostgreSQL temporal y SMTP capturado:

| Comprobación | Resultado |
|---|---|
| Navegador real | **Tres flujos pasan**: tema/acceso/error/idioma, registro/recuperación y alta completa. Login desde menú desktop, menú móvil y footer; `/login` entra directamente al IdP |
| Cuentas/OIDC/SMTP | Registro → verificación → contraseña → pendiente → aprobación/obra; alta directa → activación → contraseña → obra; suspensión bloquea sesión. Recuperación por correo cambia la contraseña y conserva identidad/aprobación/acceso a obra. La respuesta genérica de recuperación es idéntica para cuenta existente e inexistente |
| UI y accesibilidad | Capturas revisadas a 1440×1000 y 360/390 px. Teclado para login, aprobación y alta; español/inglés; cero violaciones Axe WCAG 2/2.1/2.2 AA en las pantallas ensayadas. Sin scroll horizontal. No constituye certificación ni ensayo con lector de pantalla |
| Regresión pública | **Nueve flujos pasan**: seis de landing/redirect/estados y tres de fundamentos. Conserva muestra/borradores, contenido aprobado, retirada de imágenes y restricciones del optimizador |
| Configurador | Seis escenarios de validación/dry-run pasan. Contra el IdP aislado se verificaron selección, reversión a `keycloak.v2` y aplicación repetida. Comparación completa del realm salvo `loginTheme`, cliente y usuarios sin cambios; discovery conserva el issuer. Backups con modo 600 |
| Frontend | ESLint, TypeScript estricto, dos suites Node y builds standalone Next 16.4.3 para ambos entornos aislados correctos; sin dependencias nuevas |
| Documentación/contratos | Checker de enlaces/secretos, cuatro pruebas del checker y compatibilidad OpenAPI contra M3 correctos. No se cambió backend ni migraciones; no se volvió a ejecutar Maven para este ajuste de UI/infraestructura |

**Aplicación habitual pendiente:** `docker compose ps` devolvió permiso denegado para el socket Docker del sistema. No se recreó el contenedor habitual ni se seleccionó su tema; el realm habitual y sus usuarios se conservaron. El tema quedó instalado/activado y probado solo en la distribución nativa aislada. Ejecutar los comandos de aplicación anteriores desde una terminal con acceso a Docker para verlo en el entorno habitual. El frontend habitual también debe estar activo/actualizado.

No se ejecutaron CI remota, despliegue productivo, DNS/túnel, cambio de emisor, SMTP externo ni ensayo con dominio real. Los servidores aislados se detienen al finalizar. Capturas finales: `target/theme-preview/login-desktop.png`, `login-mobile.png`, `register-mobile.png` y `recovery-mobile.png`.
