# 23 — Cuentas y accesos sin UUID manuales

**Actualización del 27 de octubre de 2026:** el recorrido principal es [alta directa y registro propio con aprobación de gerencia](25-altas-y-aprobacion-de-cuentas.md). Las invitaciones de este documento describen la entrega histórica y se conservan para enlaces previos. El selector y las revocaciones siguen disponibles; habilitar una cuenta nueva permite conceder cartera y obras en una operación.

[Índice](README.md) · [BK-010](13-roadmap-y-backlog.md) · [Permisos](03-roles-y-permisos.md) · [ADR 0015](adr/0015-cuentas-y-accesos-sin-identificadores-manuales.md)

El 13 de octubre de 2026 el usuario autorizó simplificar la vinculación para la usuaria final. Sustituye la corrección inicial que solo explicaba/validaba UUID. Implementación local sobre M0–M4; no se publica ni se conectan servicios externos nuevos.

## Recorrido de la usuaria

1. En Clientes y proveedores, abrir **Gestionar acceso de cuentas**. La lista muestra nombres/correos, acceso concedido/revocado y cuentas desactivadas.
2. Abrir **Vincular una cuenta**, buscar por nombre o correo, seleccionar una cuenta habilitada, revisar la identidad y confirmar **Conceder acceso**. Cambiar la selección reinicia la confirmación.
3. Si aún no está habilitada, abrir **Invitar a una persona** en la misma ficha. Crear y compartir el enlace; la persona inicia sesión con el correo invitado y pulsa **Aceptar invitación**. Después se busca y vincula su cuenta. La invitación concede rol, no acceso automático a todas las fichas.
4. En una obra, abrir **Gestionar acceso a la obra**. Elegir Cliente/Operación administrativa/Proveedor y buscar la cuenta. Cliente/proveedor requieren pertenecer primero a la cartera correspondiente; proveedor se elige por nombre. La representación contractual se confirma aparte.
5. Para retirar acceso, abrir la acción de revocación de la persona en la lista y confirmar. Se conserva la historia y la cuenta, incluidos registros/documentos previos.

Las cuentas existentes sin nombre se muestran por correo. El nombre se actualiza al iniciar sesión si el perfil OIDC verificado lo proporciona. No se vincula una cuenta automáticamente por coincidir con el correo del contacto.

## Contrato, permisos y datos

V9 agrega `user_account.display_name` sin cambiar IDs, roles, habilitación ni membresías. Registro OIDC usa claims verificados; no habilita cuentas desactivadas ni concede roles por el nombre. UUID siguen internos en requests/responses y keys de React; los comandos anteriores permanecen compatibles.

`POST /accounts/search` y `POST /projects/{id}/account-candidates` exigen GERENTE, sesión cookie+CSRF o bearer válido. Búsqueda en el cuerpo, evitando nombres/correos en URL/logs de acceso; lectura auditada sin guardar consulta/nombre/correo en metadata. Filtro literal, rol vigente, cuenta activa y máximo 50 resultados, con paginación. Candidatas de obra filtran además cartera activa. Directorio global denegado para asistente, cliente, proveedor y ADMIN.

`GET /clients/{id}/member-accounts`, equivalente de proveedores y de obras: solo GERENTE, identidad privada de vinculadas incluidas desactivadas/revocadas, paginación y `no-store`. El servicio del recurso autoriza antes de resolver identidades en lote. Los PUT de concesión/revocación conservan contraseña reciente, CSRF, ETag, idempotencia, auditoría y revalidación de rol/cartera/estado. UI no es el control de acceso.

Invitación anterior de un uso y 24 horas: correo verificado, hash de token y validación en servidor. Enlace nuevo usa fragmento; navegador lo retira de la URL y conserva temporalmente en sessionStorage para el login, limpiándolo al aceptar/descartar/caducar. No se copia token a query ni logs. La invitación no crea una identidad en el IdP y no afirma envío SMTP: mientras el correo real siga pendiente se comparte el enlace. El alta en el proveedor se mantiene como requisito operativo.

## Verificación y límites

Backend: perfil verificado, directorio privado/roles/estado/filtro literal/límites, cuentas vinculadas desactivadas revocables, candidatas propias/ajenas, cambio de rol tras selección e invitación de un uso/correo/contraseña reciente. Migración V8→V9 conserva identidades/roles/membresías. Suites M0–M4 conservadas; pruebas Drive/ClamAV reales siguen opt-in.

Resultados ejecutados el 13 de octubre:

| Verificación | Resultado |
|---|---|
| Backend M0–M4 + cuentas/migración | **101 pruebas pasan**, 4 opt-in omitidas (ClamAV y Google Drive reales); 105 casos en los informes. La suite focal de cuentas/V9 pasa con sus 6 casos |
| Playwright | **9 flujos pasan**: 3 fundamentos, 5 selector/invitaciones con contrato controlado y 1 recorrido PostgreSQL/Keycloak reales |
| Móvil, teclado y Axe | Clientes/proveedores a 390 px sin desbordamiento horizontal, confirmación reiniciada al cambiar cuenta, concesión y revocación por teclado; cero violaciones automatizadas WCAG 2/2.1/2.2 AA en esas vistas |
| Frontend | ESLint, TypeScript, transporte Node y build standalone temporal correctos; sin dependencias nuevas |
| Upgrade V8→V9 | Conserva ID, issuer/subject, correo, rol y membresía; nombre inicial vacío |
| Copia cifrada y restore | **61 tablas y 9 migraciones**; conteos/hashes de todas las filas iguales en DB nueva. Conserva 2 cuentas con nombres OIDC, 2 roles, 1 cartera/membresía, 1 obra/membresía revocada y 1 invitación aceptada. AES-GCM, descifrado íntegro y restauración en **2,060 s**, excluyendo arranque/smoke y objetos/IdP |
| Contratos/documentación | OpenAPI 0.4.1/cliente generado, compatibilidad contra M3 y checks de enlaces/secretos correctos |

La invitación se crea desde cartera, se acepta desde el enlace con la cuenta verificada y se concede/revoca acceso desde la UI. El cliente obtiene 200 en su portal mientras tiene acceso y 404 tras revocarlo. Los errores de búsqueda y aceptación permiten reintento; enlace caducado puede descartarse y sustituirse por otro. Cuatro pruebas de infraestructura se omiten explícitamente: esta entrega no ejecuta Drive/ClamAV reales ni revalida los ensayos productivos pendientes.

El servidor habitual del usuario en 3000 se conserva durante los ensayos: frontend de pruebas en 3301, backend/DB de harness en 9192. El test captura la respuesta del login antes de su redirección y reemplaza únicamente el destino del callback en su contexto de navegador; el intercambio del código conserva el redirect_uri registrado en Keycloak. No modifica el realm ni sus usuarios. Fixtures `example.invalid`, sin membresías empresariales reales modificadas. CI habilita `ATO_E2E_ACCOUNTS=1` únicamente en el harness aislado.

`scripts/verify-account-restore.py --synthetic` verifica copia AES-GCM, restauración de DB/ACL/nombres/invitaciones a una base nueva y hashes de todas las tablas; rechaza cuentas/cartera/invitaciones fuera de `example.invalid`. No sustituye el ensayo completo de objetos/IdP ni acredita RPO/RTO productivos. La clave queda fuera del directorio de copia, mode 600.

Antes de usar esta versión, reiniciar backend para aplicar V9 y cargar endpoints nuevos; recargar frontend de desarrollo o reconstruir/reiniciar standalone. Gates de producción en [22](22-operacion-m4.md) siguen vigentes.
