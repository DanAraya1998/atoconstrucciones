# Infraestructura de desarrollo

ClamAV conserva configuración/límites en [clamd.conf](clamd.conf). PostgreSQL/Keycloak/ClamAV se describen en [Compose](../compose.yaml).

Keycloak usa [tema ATO](keycloak/themes/ato/login/theme.properties) heredado de `keycloak.v2`. El volumen del tema es solo lectura; un realm existente se actualiza con `scripts/configure-keycloak-theme.py --apply` después de recrear el servicio. [Configuración, rollback y ensayo](../docs/28-login-con-identidad-ato.md); no cambiar el issuer para aplicar estilos.

Google Drive es el almacenamiento activo elegido por el cliente: [configuración](../docs/18-almacenamiento-google-drive.md). No hay S3Mock ni AWS SDK activos. `s3-application-policy.json` y `s3-bucket-policy.json` son plantillas históricas de M1 anteriores a ADR 0011; conservarlas como referencia de migración, no aplicarlas. No eliminar volúmenes/objetos previos sin inventario y hashes comprobados.

M4 prepara [entorno productivo](production.env.example), [Cloudflare Tunnel](cloudflared.config.example.yml) y [runbook](../docs/22-operacion-m4.md). Plantillas sin credenciales; no ejecutan despliegue.
