# Architecture Decision Records

[Índice principal](../README.md) · [Arquitectura](../04-arquitectura-tecnica.md) · [Pendientes](../01-vision-y-alcance.md)

## Convenciones

Numeración correlativa `NNNN-titulo.md`, sin renumerar ADR publicados. Estados: **Aceptado** (instrucción confirmada o decisión explícita), **Propuesto** (requiere resolver alternativas/evidencia), **Rechazado**, **Sustituido** (enlazar reemplazo). No confundir una propuesta con permiso para cambiar el stack.

Fecha de revisión inicial: 7 de octubre de 2026. El estado de implementación se documenta en [16](../16-entrega-m0.md); un ADR por sí solo no acredita pruebas. Requisitos confirmados no se someten a aprobación repetida; las decisiones propuestas se resuelven cuando una tarea necesite concretarlas, documentando consecuencias y pruebas.

## Índice

| ADR | Estado | Contenido / decisión pendiente |
|---|---|---|
| [0001 — Monolito modular y API independiente](0001-monolito-modular-y-api.md) | Aceptado por pedido | Backend Spring con reglas reutilizables; REST versionada, frontend y Flutter desacoplados |
| [0002 — Stack, Java 27 y Boot 4.2.0](0002-stack-y-java-27.md) | Aceptado por pedido actualizado | Boot 4.2.0-M2 aceptada para M0/Java 27, build y arranque verificados; estabilidad productiva pendiente |
| [0003 — Documentos en objetos privados](0003-documentos-en-objetos.md) | Sustituido por ADR 0011 | Propuesta histórica S3; Google Drive elegido por el cliente |
| [0004 — Autorización y soporte excepcional](0004-autorizacion-y-soporte.md) | Implementado en M0 sintético | Conservar exclusividad GERENTE; grant limitado para ADMIN, adaptación empresarial pendiente |
| [0005 — Hechos financieros y reversiones](0005-hechos-financieros.md) | Parcialmente implementado en M2; ver 0012 | Modelo de control operativo trazable, sin contabilidad completa; P-03/P-05/P-06/P-07 |
| [0006 — OIDC y sesiones/API](0006-oidc-y-sesiones.md) | Aceptado para desarrollo | Keycloak local, OIDC/PKCE/sesiones JDBC/bearer; MFA desactivado; P-02 productivo pendiente |
| [0007 — Gantt y avance ponderado](0007-gantt-y-avance.md) | Implementado M3; ver 0013 | Calendario continuo/FS/pesos físicos; alternativas laborales/PRO pendientes |
| [0008 — UI, tablas y gráficos](0008-ui-tablas-y-graficos.md) | Propuesto | Componentes/tokens y selección Table/Recharts, validación móvil/licencias |
| [0009 — Fundamentos ejecutables M0](0009-fundamentos-ejecutables-m0.md) | Implementado en M0 | Recurso sintético, JDBC/PG real, contrato estático, pruebas y límites |
| [0011 — Google Drive para evidencias](0011-google-drive-evidencias.md) | Aceptado; adaptador implementado | Sustituye S3, conserva permisos/cuarentena e identifica referencias anteriores; conexión real pendiente |
| [0012 — Operación financiera y XML M2](0012-operacion-financiera-y-xml-m2.md) | Implementado para desarrollo | Hechos/aplicaciones/revisiones, permisos/cortes y XSD oficial local; revisión fiscal productiva pendiente |
| [0013 — Planificación y portal M3](0013-planificacion-y-portal-m3.md) | Implementado para desarrollo | DAG/revisión global, avance verificado, snapshots/eventos y proyecciones cliente/proveedor |
| [Plantilla](plantilla.md) | Referencia | Estructura para nuevas decisiones |

## Cuándo crear o sustituir un ADR

Cambio de stack/versión base, proveedor de identidad/storage, distribución de módulos, forma de autenticar, autoridad financiera, moneda/impuestos, reglas de avance, contrato API incompatible, retención/soporte o integración bidireccional. Ajustes locales de label/estilo sin impacto arquitectónico solo requieren documentación pertinente.

Cada ADR enlaza historias, pendientes, tareas y pruebas; actualizar documentos canónicos al aceptar. No borrar contexto ni alternativas descartadas si explican consecuencias; no copiar extensamente la especificación. Ante nueva evidencia contradictoria, registrar propuesta/sustitución y resolver antes de entregar una tarea dependiente.

## M1

[0010 — Obras, contratos y evidencias](0010-obras-contratos-evidencias-m1.md), aceptado para desarrollo M1 autorizado. Conserva la selección histórica S3, posteriormente sustituida por ADR 0011. Contratos/permisos del hito permanecen vigentes; conexión Drive productiva pendiente.

- [0014 — Publicación, contacto y operación M4](0014-publicacion-contacto-operacion-m4.md): implementación local; Cloudflare Tunnel/Gmail y release recuperable.
- [0015 — Cuentas y accesos](0015-cuentas-y-accesos-sin-identificadores-manuales.md): implementación autorizada de búsqueda/confirmación, perfiles verificados e invitación por enlace, conservando permisos del servidor.
- [0017 — Altas y aprobación](0017-altas-y-aprobacion-de-cuentas.md): alta directa, registro propio pendiente y aprobación con cartera/obras; Keycloak y activación durable. Se conserva el documento previo 0016 de conversión de contactos con su propio alcance.
- [0018 — Reprogramación y categorías de proveedor](0018-reprogramacion-y-categorias-de-proveedor.md): fechas/duración/desplazamiento, espacio Gantt ampliado y cartera categorizada sin obra obligatoria; V11 expansiva.
