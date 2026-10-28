# 03 — Roles y permisos

Actualización 27/10/2026: registrar proveedor sin obra/cuenta y editar su categoría conserva la autoridad de cartera: GERENTE crea; asistente solo edita en su alcance de obras. Categorizar no concede acceso. Reprogramar conserva GERENTE/OPERATIONS y validación completa del cronograma; proveedor/cliente/ADMIN no adquieren edición. [Flujos y pruebas](26-reprogramacion-y-proveedores.md).

Actualización 27/10/2026: [altas y aprobación](25-altas-y-aprobacion-de-cuentas.md) añade gestión exclusiva de GERENTE. PENDING/REJECTED no reciben roles efectivos aunque existan filas de rol; suspensión bloquea cada petición. Aprobar concede perfil operativo, cartera y obras explícitas, nunca ADMIN/GERENTE. El correo debe estar verificado; las invitaciones históricas no reviven cuentas rechazadas/suspendidas.

[Índice](README.md) · [Seguridad](11-seguridad-y-auditoria.md) · [API](06-api-y-openapi.md)

## Modelo de autorización propuesto

**RN-A01:** deny-by-default. El rol habilita una capacidad; el vínculo al recurso, su estado y la audiencia del campo completan la decisión. El frontend puede ocultar controles, pero nunca es el control de seguridad.

**RN-A02:** CLIENTE necesita `ProjectMember` activo ligado al contratante y capacidad de representante cuando corresponde. PROVEEDOR necesita `SupplierMember` activo y `ProjectAssignment`/`TaskAssignment`. GERENTE tiene ámbito empresarial completo. Asistente trabaja sobre proyectos operativos autorizados; se propone membresía explícita `OPERATIONS`, con alcance de todos los proyectos activos otorgable por GERENTE (P-04). Sus consultas siguen siendo por obra.

**RN-A03:** proveedor puede operar exclusivamente sus ofertas, consumos y tareas asignadas; compartir un proyecto no comparte costos/competidores/clientes. Revocar asignación deja de habilitar nuevos accesos; la consulta histórica al proveedor exige grant explícito de solo lectura.

**RN-A04:** personal exige capacidad GERENTE y titularidad del ámbito personal; roles adicionales nunca satisfacen esa regla. Datos/archivos/exports/búsquedas personales se excluyen de agregados y endpoints empresariales.

**RN-A05:** dashboard financiero consolidado de empresa, cuentas bancarias completas, importaciones y exportación global requieren GERENTE. Asistente recibe una cola de seguimiento por obra (saldo de cobro, vencimiento acordado, último contacto), sin totales del negocio, márgenes agregados, cuentas completas ni consultas que eludan esta restricción. La información por obra permite inferencias parciales; si se exige impedir también esa inferencia habrá que reducirla en P-03.

**RN-A06:** integraciones requieren capacidad funcional y autorización OAuth del titular; un rol local no concede acceso externo.

**RN-A07:** documentos/auditoría heredan la clasificación más restrictiva de su recurso y requieren autorización propia. Un objeto compartido no hace público el resto del proyecto; cualquier adjunto de finanzas personales se almacena bajo ese ámbito.

**RN-A08:** ADMIN gestiona estado técnico y cuentas de menor privilegio dentro de procedimientos auditados; no puede darse GERENTE, modificar titularidad, otorgarse soporte, resetear MFA/credenciales de GERENTE para entrar como ella, ni usar impersonación. Alta/cambio de privilegios GERENTE y recuperación de la última gerente requieren procedimiento de sucesión acordado, fuera de un grant de soporte corriente.

## Matriz de acciones

Leyenda: **Sí** ámbito empresarial de GERENTE; **Obra** proyectos operativos autorizados; **Propio** membresía/propiedad verificada; **Asignado** asignación vigente; **Téc.** metadata/operación técnica sin payload; **No** denegado. Las acciones de aprobación descritas son una propuesta conservadora pendiente P-03/P-07; la exclusividad de balances personales/globales de GERENTE es confirmada.

| Recurso / acción | ADMIN | GERENTE | ASISTENTE_ADMINISTRATIVA | PROVEEDOR | CLIENTE |
|---|---|---|---|---|---|
| Diagnóstico de salud, despliegue y versión | Téc. | Estado de servicio | No | No | No |
| Invitar/desactivar usuario de menor privilegio | Téc., sin escalar roles | Sí | No | No | No |
| Otorgar roles/membresías operativas | No, salvo procedimiento técnico validado | Sí | No | No | No |
| Conceder GERENTE o recuperación de última gerente | No por acceso ordinario | Sucesión documentada | No | No | No |
| Datos propios de perfil/contacto | Propio | Propio | Propio | Propio | Propio |
| Clientes: crear/editar contacto operativo | No | Sí | Obra, sin cambiar contratante | No | Propio: solicitar corrección |
| Proveedores: crear/editar contactos/categorías | No | Sí | Obra, datos operativos | Propio: solicitar corrección | No |
| Proyecto: crear, cliente contractual, activar/cancelar | No | Sí | Preparar borrador, formaliza gerente | No | No |
| Proyecto: leer ficha operativa | Téc., anonimizada | Sí | Obra | Asignado, DTO mínimo | Propio, DTO mínimo |
| Ubicación/datos de contacto privados | No | Sí | Obra por necesidad | Asignado, mínimo para visita | Propio |
| Cambiar contacto/fecha informativa | No | Sí | Obra, cambio auditado; no fecha contractual | No | No |
| Contrato: modificar importes, plazo contractual/enmiendas | No | Sí | No | No | No |
| Contrato vigente/extras aprobados/saldo de cobro | No | Sí | Obra | No | Propio |
| Costos/pagos operativos por obra | No | Sí | Obra | Solo sus consumos/compromiso | No |
| Cobros: reportar y adjuntar | No | Sí | Obra | No | Propio |
| Cobros: revisar/solicitar evidencia | No | Sí | Obra | No | Solo aportar evidencia propia |
| Cobros: confirmar/rechazar definitivamente/revertir | No | Sí | No (P-03) | No | No |
| Gastos: registrar/revisar/imputar propuesta | No | Sí | Obra | Asignado: reporte propio | No |
| Gastos: validar/anular costo reconocido | No | Sí | No (P-03) | No | No |
| Pagos: preparar propuesta y evidencia | No | Sí | Obra | No | No |
| Pagos: confirmar/devolver/revertir | No | Sí | No (P-03) | No | No |
| Cotización: crear/revisar borrador manual | No | Sí | Obra | Asignado: propia | No |
| Cotización: enviar versión al negocio | No | Sí, origen manual | Obra, origen manual | Asignado: propia | No |
| Cotización: aprobar/rechazar/sustituir compromiso | No | Sí | No | No | No |
| Comparar ofertas competidoras/margen | No | Sí | Ofertas operativas por obra; sin margen global | No | No |
| Extra: solicitar | No | Sí | Obra: registrar por encargo, origen explícito | No | Propio |
| Extra: cotizar precio al cliente | No | Sí | Preparar por obra | Asignado: oferta de costo, no precio al cliente | No |
| Extra: aceptar versión de alcance/precio | No | Registrar evidencia | No | No | Propio, representante (P-07) |
| Extra: formalizar aprobación/cancelación | No | Sí con evidencia | No | No | No |
| Gantt administrativo: consultar/editar fechas/tareas | No | Sí | Obra, operativas; sin formalizar baseline | Asignado: vista/observaciones | No |
| Reportar progreso | No | Sí | Obra | Asignado | No |
| Aprobar pesos, alcance y progreso público | No | Sí | No | No | No |
| Avance cliente/fecha estimada resumida | No | Sí | Obra | Solo tareas asignadas | Propio |
| Bancos: importar/clasificar/conciliar/corregir | No | Sí | No | No | No |
| Caja: reporte de operación manual | No | Sí | Obra: preparar | No | Reportar depósito propio |
| Dashboard financiero global/exportación empresarial | No | Sí | No | No | No |
| Cola de cobros por obra | No | Sí | Obra | No | Su saldo |
| Finanzas personales/cuentas/exports | No | Solo titular GERENTE | No | No | No |
| Documentos privados: subir/descargar | No | Sí según ámbito | Obra según audiencia | Propio/Asignado compartido | Propio compartido |
| Cambiar audiencia o compartir evidencia | No | Sí | Proponer por obra | No | No |
| Auditoría funcional de obra | Téc., sin valores sensibles | Sí | Resumen operativo por obra | Eventos propios permitidos | Historial público de su trámite |
| Configurar Calendar/invitar proveedores | No | Sí con OAuth/consentimiento | No | No | No |
| Cerrar/reabrir obra | No | Sí | Preparar checklist | No | No |
| Consentimiento/publicar/retirar ficha e imágenes | No | Sí con evidencia | Preparar borrador | No | Aportar/retirar consentimiento propio |
| Contactos públicos: revisar/responder fuera de plataforma | No | Sí | Operación delegada | No | No |
| Auditoría de acceso excepcional | Téc.: propios grants | Sí | No | No | No |

## Evaluación por recurso y por campo

Aplicar política a `GET` por ID, lista paginada, joins, filtros, búsqueda, estadísticas, eventos, jobs y descargas. Autorizar padre y todos los IDs hijos (`extraId`, proveedor, comprobante, cotización); rechazar relaciones de distinta obra/ámbito. DTO públicos, operativos, cliente, proveedor y gerente tienen allowlists distintas; no serializar entidades ni “ocultar en React”.

Para personas externas, devolver 404 ante inexistencia o recurso ajeno sin revelar cuál; 403 para acción denegada sobre un recurso que ya pueden conocer. Validar roles/capacidades de servidor en cada comando; membresías vigentes no se confían a un JWT de larga duración. Cache por identidad/audiencia y revocación, nunca compartida entre portales.

GERENTE puede operar proyectos cerrados solo mediante reapertura o comando correctivo autorizado. Todo cambio de roles, miembros, aprobaciones, dinero, audiencia o publicación se audita. Un listado no debe aceptar un filtro “todos” que retire la cláusula de alcance.

## ADMIN: soporte excepcional propuesto

No hay “superusuario” de negocio. Los privilegios de sistema operativo/DB del operador pueden permitir acceso material fuera de RBAC: se reduce mediante cuentas separadas, cifrado/claves controladas, mínimo acceso de infraestructura y registro externo. RBAC por sí solo no protege ante un administrador de infraestructura con todas las claves.

1. ADMIN abre solicitud indicando incidente, recurso/columnas, propósito y plazo; primero usa diagnósticos anonimizados.
2. GERENTE autoriza un `SupportGrant` concreto, de solo lectura y duración propuesta máxima de 30 minutos. Ámbito personal requiere autorización explícita separada de su titular; un grant empresarial no lo cubre.
3. Backend verifica reautenticación reciente (MFA opcional, desactivado en M0), grant activo, alcance/acción y caducidad. No se cambia el rol ni se entrega sesión de la gerente.
4. Auditar apertura, cada acceso/exportación permitida y terminación en destino protegido. Exportación deshabilitada por defecto; grant no habilita confirmaciones financieras ni modificación de claves.
5. GERENTE puede revocar inmediatamente; ADMIN entrega resumen del incidente. Expira automáticamente y se revisa el acceso.

Si no hay gerente disponible, se permiten diagnóstico y recuperación de servicio sin lectura sensible; no se inventa consentimiento por emergencia. Sucesión y acceso a backups sensibles se deben acordar en P-04/P-10. [ADR de soporte](adr/0004-autorizacion-y-soporte.md).

## Aplicación M1

P-04 confirmado el 8 de octubre: un contratante, representantes y OPERATIONS explícitos. `party_member` y `project_member` se verifican con roles actuales en cada servicio, lista y descarga; asignar una parte no asigna todas sus obras. El usuario mantiene CRC inicial. ADMIN/grants M0 no heredan acceso M1. Reautenticación en formalización, estados, membresías, audiencias y aprobación de avisos. Proveedor carece de contrato/extras y de evidencia contractual, aun con asignación operativa.

Asistente puede preparar obra para cliente de su ámbito ya autorizado, editar ficha/fecha informativa y contacto; no cambia identidad/estado de la parte. Contrato/fecha contractual se modifica exclusivamente mediante GERENTE. [ADR M1](adr/0010-obras-contratos-evidencias-m1.md).

## Aplicación M2

P-03 se implementa con el predeterminado conservador: GERENTE confirma/valida/revierte y autoriza excepciones con contraseña reciente; asistente prepara/revisa/corrige propuestas de sus obras OPERATIONS. CLIENTE reporta y consulta cobros propios. ADMIN/PROVEEDOR no acceden a costos/caja/consolidado M2. Documento financiero interno no se comparte fuera de operación. El consolidado es GERENTE; la cola mantiene proyección por obra. [Código, pruebas y límites](19-entrega-m2.md) · [ADR 0012](adr/0012-operacion-financiera-y-xml-m2.md).

## Aplicación M3

GERENTE/OPERATIONS editan cronograma y reportan; GERENTE aprueba pesos/verifica con contraseña reciente. PROVEEDOR necesita membresías vigentes y asignación a la tarea; solo recibe su DTO y la fecha mínima impuesta por dependencias externas, sin IDs/nombres externos. Cliente usa fichas/trámites/documentos propios y progreso/hitos verificados; nunca consulta `/schedule`. Nuevas proyecciones `/client-projects` y `/client-portal` excluyen actores, proveedores, movimientos y costos internos. Revocar acceso bloquea lecturas y replays. [Evidencia y límites](20-entrega-m3.md).

## Aplicación M4

GERENTE revisa/aprueba textos y publicación, retira fichas, delega contacto y reencola notificaciones con contraseña reciente. OPERATIONS prepara/consulta fichas y consentimientos web de su obra vigente; no publica. Portafolio independiente y lista editorial global son de gerencia. Asistente consulta la bandeja de contactos solo tras delegación activa; revocarla bloquea la próxima lectura. ADMIN consulta únicamente `/operations/status` con conteos técnicos. Público solo ve DTO editorial aprobado y variantes autorizadas. [Evidencia y límites](21-entrega-m4.md).

## Gestión de cuentas por nombre/correo

GERENTE consulta el directorio privado, las identidades vinculadas y las candidatas de una obra. ADMIN, asistente, cliente y proveedor no consultan ese directorio. Cliente/proveedor candidatos requieren rol vigente, cuenta habilitada y membresía activa en la cartera correspondiente; operación requiere ASISTENTE_ADMINISTRATIVA. La selección no concede acceso: el PUT existente revalida permisos, contraseña reciente, versión, rol y cartera. Las cuentas vinculadas desactivadas permanecen visibles para revocar. Aceptar una invitación concede el rol autorizado y no membresías automáticas. [Flujo y pruebas](23-cuentas-y-accesos.md).
