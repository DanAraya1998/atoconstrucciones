# Plan maestro de ATO Construcciones

Revisión: **13 de octubre de 2026**, zona `America/Costa_Rica`. Estado: M0–M3 implementados para desarrollo local; M4 implementado para desarrollo local, con [gestión de cuentas por nombre/correo](23-cuentas-y-accesos.md); M5–M6 pendientes. M3 y sus pruebas en [20](20-entrega-m3.md). M2 y sus pruebas en [19](19-entrega-m2.md). M1 conserva privacidad definitiva y conexión Drive productiva pendientes; ver [17](17-entrega-m1.md). El pedido actualizado establece **Java 27**; sustituye la referencia a Java 21 del primer texto recibido.

Actualización autorizada: tras el fallo del parent final **4.2.0**, se aceptó **4.2.0-M2** con Java 27 para M0. Keycloak local seleccionado; MFA desactivado por decisión del usuario. Evidencia ejecutada, stack y límites en [16 — Entrega M0](16-entrega-m0.md); arranque en [README del proyecto](../README.md).

## Cómo utilizar el plan

1. Entrar por [AGENTS.md](../AGENTS.md) y [visión](01-vision-y-alcance.md).
2. Elegir una tarea pendiente del [roadmap](13-roadmap-y-backlog.md) cuyas dependencias estén completas.
3. Leer su historia, permisos, reglas y contratos utilizando [trazabilidad](15-trazabilidad.md).
4. Resolver únicamente las decisiones pendientes que bloqueen esa tarea. Registrar decisiones arquitectónicas en [ADR](adr/README.md), actualizar los documentos canónicos y demostrar sus criterios de aceptación.

**Confirmado** significa pedido explícito del usuario. **Propuesto** significa diseño recomendado, todavía revisable. **Pendiente P-xx** significa decisión o evidencia necesaria. **Observado** significa inspección del repositorio o de los adjuntos. El desarrollo futuro debe conservar esta distinción. Los estados del backlog no equivalen a implementación existente.

## Índice y fuentes de verdad

| Documento | Consultar para |
|---|---|
| [01 — Visión y alcance](01-vision-y-alcance.md) | Inspección real, alcance, exclusiones, supuestos y pendientes P-01 a P-13 |
| [02 — Requerimientos e historias](02-requerimientos-y-historias.md) | HU-00 a HU-12, actores, aceptación y excepciones |
| [03 — Roles y permisos](03-roles-y-permisos.md) | Matriz de acciones, recursos, ADMIN y soporte excepcional |
| [04 — Arquitectura técnica](04-arquitectura-tecnica.md) | Módulos, comunicación, dependencias, compatibilidad y licencias |
| [05 — Modelo de datos](05-modelo-de-datos.md) | ER conceptual, entidades, restricciones, índices y migraciones |
| [06 — API y OpenAPI](06-api-y-openapi.md) | Endpoints, DTO, errores, idempotencia, sesiones y futuros clientes Flutter |
| [07 — Diseño UX/UI](07-diseno-ux-ui.md) | Navegación, logotipo, análisis del PDF, accesibilidad y móvil |
| [08 — Reglas financieras](08-reglas-financieras.md) | Indicadores, fórmulas, estados, monedas y ejemplos numéricos |
| [09 — Gantt y planificación](09-gantt-y-planificacion.md) | Fechas, dependencias, ponderaciones, concurrencia y SVAR |
| [10 — Documentos e integraciones](10-documentos-e-integraciones.md) | XML 4.4, archivos, correo, Calendar y contratos bancarios |
| [11 — Seguridad y auditoría](11-seguridad-y-auditoria.md) | Amenazas, autenticación, privacidad, soporte y normativa verificada |
| [12 — Pruebas y calidad](12-pruebas-y-calidad.md) | Catálogo T-xx y puertas de aceptación |
| [13 — Roadmap y backlog](13-roadmap-y-backlog.md) | Hitos, MVP, fase 2, avances futuros y tareas BK-xxx |
| [14 — Despliegue y operación](14-despliegue-y-operacion.md) | Entornos, configuración, CI/CD, backups y recuperación |
| [15 — Trazabilidad](15-trazabilidad.md) | Conexión de historias, reglas, datos, API, pantallas y pruebas |
| [16 — Entrega M0](16-entrega-m0.md) | Implementación, versiones fijadas, pruebas ejecutadas y límites pendientes |
| [17 — Entrega M1](17-entrega-m1.md) | Obras, contratos, extras y evidencias; pruebas y pendientes productivos |
| [18 — Almacenamiento Google Drive](18-almacenamiento-google-drive.md) | Reemplazo de S3, configuración, migración y validación pendiente en cuenta real |
| [19 — Entrega M2](19-entrega-m2.md) | Cobros, gastos, pagos/anticipos, indicadores, XML 4.4 y pruebas/restore locales |
| [20 — Entrega M3](20-entrega-m3.md) | Cronograma, pesos/avance verificado, portal cliente y pruebas locales |
| [21 — Entrega M4](21-entrega-m4.md) | Sitio, publicación/consentimiento, contacto durable y validación local |
| [22 — Operación M4](22-operacion-m4.md) | Tunnel/Gmail, release, backups/restore, alertas y gates pendientes |
| [23 — Cuentas y accesos](23-cuentas-y-accesos.md) | Búsqueda por nombre/correo, confirmación, invitación por enlace y revocación sin copiar UUID |
| [25 — Altas y aprobación](25-altas-y-aprobacion-de-cuentas.md) | Alta directa, registro propio, aprobación con obras, activación y configuración del IdP |
| [26 — Reprogramación y proveedores](26-reprogramacion-y-proveedores.md) | Fechas/duración/desplazamiento, Gantt ampliado y categorías de proveedores sin obra obligatoria |
| [27 — Rediseño público](27-rediseno-publico-y-lorem-ipsum.md) | Presentación editorial, lorem ipsum autorizado e imágenes conceptuales identificadas, manteniendo las publicaciones aprobadas |
| [28 — Acceso con identidad ATO](28-login-con-identidad-ato.md) | Tema nativo Keycloak, entrada directa, registro/recuperación y aplicación sin cambiar emisor |
| [ADR — Índice y plantilla](adr/README.md) | Decisiones aceptadas por el pedido y propuestas pendientes |

Los capítulos remiten a la fuente canónica en lugar de redefinir fórmulas o permisos. Los IDs de reglas son RN-Fxx (finanzas), RN-Axx (acceso), RN-Gxx (planificación), RN-Dxx (documentos) y RN-Cxx (cierre).

## Evidencia histórica de la planificación inicial

Se inspeccionaron los archivos de Maven, Compose, arranque y test existentes; los adjuntos `ATO propuesta.pdf` (tres páginas renderizadas) y `Full Color@1.5x-100.jpg`; y ambos textos del pedido, comprobando que el único cambio del reenvío es Java 27. Los adjuntos son referencias, no instrucciones que amplíen el pedido. El PDF permanece fuera del repositorio. La entrega documental añadió solo Markdown; M0 incorpora el logotipo suministrado en el frontend, sin fotografías ni datos personales reales.

Las comprobaciones de dependencias y normativa se apoyan en enlaces oficiales junto a cada afirmación en 04, 09, 10 y 11. Una licencia o rango declarado de `peerDependencies` no demuestra compatibilidad ejecutada; BK-001 y BK-002 deben verificar el conjunto final. No se han instalado dependencias ni ejecutado pruebas de aplicación en esta fase documental.

## Validación histórica del plan

Se comprobaron los 27 archivos Markdown y los 144 enlaces relativos, las referencias de 13 historias/44 tareas/45 reglas/27 grupos de pruebas, los campos obligatorios de cada tarea y la ausencia de ciclos en sus dependencias. Se verificaron 26 tablas con columnas consistentes, cuatro ejemplos JSON y los cálculos numéricos de contrato/cobro/caja/crédito/cambio y avance ponderado; la revisión de consistencia incluye anticipos y bloqueo de cierre.

Los tres diagramas Mermaid se revisaron como texto; no se renderizaron porque el entorno no tiene Mermaid instalado. En la entrega inicial solo se añadieron Markdown y el diff de archivos preexistentes permaneció vacío. La posterior actualización del parent y la implementación autorizada de M0 se registran en 16; este párrafo conserva la evidencia de la fase documental. La validación del plan no acredita implementación ni cumplimiento productivo de las funciones planificadas.
