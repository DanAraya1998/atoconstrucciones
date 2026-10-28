# ATO Construcciones — guía para agentes

## Entrada y estado

Leer [el índice del plan maestro](docs/README.md), la historia y la tarea asignadas en [el backlog](docs/13-roadmap-y-backlog.md), y sus filas de [trazabilidad](docs/15-trazabilidad.md). La entrega inicial fue documental; posteriormente el usuario autorizó M0. Los fundamentos están implementados con recurso sintético; M1–M3 están implementados para desarrollo local y M4 implementado para desarrollo local; M5–M6 siguen pendientes. Leer [entrega y límites M4](docs/21-entrega-m4.md) y [operación M4](docs/22-operacion-m4.md). Leer [entrega y límites M3](docs/20-entrega-m3.md). Leer [entrega y límites M2](docs/19-entrega-m2.md). Leer también [entrega y límites M1](docs/17-entrega-m1.md). Leer [entrega y límites M0](docs/16-entrega-m0.md) antes de continuar.

## Stack y límites

Acceso visual 27/10/2026: [tema nativo ATO](docs/28-login-con-identidad-ato.md) sobre `keycloak.v2`, entrada `/login` directa y configurador local sin reimportación. Mantener formularios/acciones/scripts del IdP. Estilos no cambian el emisor: las identidades dependen de `(issuer, subject)`; migrar al dominio `/idp` requiere ensayo específico.

Ajuste público 27/10/2026: [landing editorial y lorem ipsum](docs/27-rediseno-publico-y-lorem-ipsum.md), CA-00.6. Texto de muestra autorizado y conceptos identificados como ilustrativos; los DTO reales aprobados conservan prioridad y los borradores no se renderizan. Next permite optimizar solo el logo y tres paths estáticos de conceptos; las fotos del API siguen `unoptimized`/`no-store`. No acredita el gestor de álbumes independiente, que sigue pendiente.

Ajuste 27/10/2026: [reprogramación/proveedores](docs/26-reprogramacion-y-proveedores.md), [ADR 0018](docs/adr/0018-reprogramacion-y-categorias-de-proveedor.md) y V11: categoría principal controlada con detalle histórico, cartera sin obra obligatoria, reprogramación mediante lote M3 y Gantt ampliado. Categorías múltiples/auto-scheduling permanecen pendientes.

Actualización de cuentas: leer [flujo sin UUID manuales](docs/23-cuentas-y-accesos.md) y [ADR 0015](docs/adr/0015-cuentas-y-accesos-sin-identificadores-manuales.md). V9 guarda nombres OIDC; selector privado de GERENTE, invitación por enlace y accesos de cartera/obra separados. El envío Gmail de invitaciones y alta en IdP productivo siguen pendientes.

Actualización 27/10/2026: [altas y aprobación](docs/25-altas-y-aprobacion-de-cuentas.md), [ADR 0017](docs/adr/0017-altas-y-aprobacion-de-cuentas.md) y V10 añaden alta directa, autorregistro pendiente, decisiones GERENTE y cola de activación Keycloak. Los enlaces anteriores se conservan; SMTP/IdP productivos requieren configuración/ensayo. El documento 24/ADR 0016 conserva la conversión de contactos con su propio alcance y no acredita esa función por la entrega de cuentas.

- **Java 27 y Boot 4.2.0-M2**: preview aceptada por el usuario para M0 tras no resolverse el parent final 4.2.0. Maven, MVC/Security/JPA/Validation/Flyway y PostgreSQL 18.6; conservar el BOM. Release estable y revalidación pendientes antes de producción. [ADR 0002](docs/adr/0002-stack-y-java-27.md).
- Frontend M0: Next.js, React, TypeScript, Tailwind CSS, shadcn/ui, Motion, TanStack Query y SVAR React Gantt Community. GSAP requiere una necesidad visual documentada. Las bibliotecas adicionales son propuestas: [evaluación](docs/04-arquitectura-tecnica.md).
- Backend como monolito modular; REST `/api/v1`, OpenAPI; reglas de negocio en Spring, reutilizables por Flutter Android/Windows. PostgreSQL conserva datos y metadatos; Google Drive es el almacenamiento elegido para archivos. [ADR 0011](docs/adr/0011-google-drive-evidencias.md) sustituye S3; conexión y validación en la cuenta real pendientes. No reintroducir S3 sin instrucción.
- Conservar Maven y `src/` en la raíz; frontend en `frontend/`. No mover la estructura por conveniencia. Paquetes por módulo bajo `com.daraya.atoconstrucciones`; consultar [arquitectura](docs/04-arquitectura-tecnica.md).

## Convenciones operativas

- Java: clases/DTO en inglés, camelCase, DTO separados de entidades, constructor injection, validación en la frontera y transacciones en casos de uso. Ningún controlador accede directamente a repositorios de otro módulo.
- TypeScript estricto; funcionalidades en `frontend/src/features/`, componentes compartidos en `frontend/src/components/`; el cliente generado no contiene reglas financieras. Textos de interfaz/documentación en español.
- Dinero con `BigDecimal`/`numeric`, serializado como string y moneda ISO; nunca `double` ni sumas entre monedas sin conversión registrada. Fechas civiles como `LocalDate`; eventos como `Instant`; presentación `es-CR`, `America/Costa_Rica`.
- Nunca reemplazar código o trabajo ajeno sin inspeccionarlo. No instalar dependencias, cambiar configuración, publicar, ni iniciar el primer hito en una sesión autorizada solamente para planificación.

## Seguridad e integridad

- Keycloak local es el IdP de desarrollo. MFA está desactivado por decisión del usuario; acciones sensibles requieren autenticación con contraseña de los últimos cinco minutos. No reintroducir MFA obligatorio sin instrucción. [ADR 0006](docs/adr/0006-oidc-y-sesiones.md).
- Aplicar [RBAC y autorización por recurso](docs/03-roles-y-permisos.md) en cada lectura, mutación, listado, exportación y descarga. ADMIN no hereda GERENTE ni consulta finanzas sensibles por defecto.
- Distinguir reportado/confirmado, registrado/validado/pagado y solicitado/aprobado; usar [las reglas financieras](docs/08-reglas-financieras.md) como fuente única. Transiciones críticas: autorización, idempotencia, control de concurrencia y auditoría en la misma transacción.
- Sin secretos ni datos personales reales en Git, ejemplos, logs o fixtures. Sin permisos públicos ni enlaces Drive que eludan los controles de evidencias, parsing remoto de XML, DTD/XXE, ni publicación de fotos sin consentimiento.
- No eliminar registros confirmados: reversión vinculada y motivo. Finanzas personales separadas, accesibles funcionalmente solo por GERENTE. [Seguridad](docs/11-seguridad-y-auditoria.md).

## Migraciones, pruebas y documentación

- Flyway versionado en `src/main/resources/db/migration/`, implementado en M0. No editar migraciones aplicadas, usar `ddl-auto=validate`; cambios expansivos antes de retirar columnas. Ensayar actualización y restauración en PostgreSQL; no ejecutar `clean` en entornos compartidos.
- Toda funcionalidad necesita HU/BK, criterios de aceptación, política de permisos, estados/errores, contratos OpenAPI, migraciones cuando correspondan y pruebas pertinentes. [Calidad](docs/12-pruebas-y-calidad.md) define las pruebas obligatorias por riesgo.
- Cambios financieros: pruebas de cálculos/reversiones/duplicados/concurrencia. Cambios de acceso: pruebas entre identidades y proyectos. Archivos/XML: pruebas hostiles. UI: flujo E2E, teclado y móvil cuando corresponda. No afirmar checks que no se ejecutaron.
- Actualizar la documentación canónica y trazabilidad en la misma entrega. Marcar propuestas y pendientes; no presentar supuestos como acuerdos del negocio.
- **No introducir cambios arquitectónicos sin ADR**: [índice y plantilla](docs/adr/README.md). Un ADR propuesto no autoriza sustituir el stack confirmado. Reevaluar versiones, licencias, mantenimiento y avisos de seguridad antes de instalar.
