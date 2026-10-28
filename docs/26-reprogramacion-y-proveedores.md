# 26 — Reprogramación, espacio Gantt y proveedores

[Índice](README.md) · [ADR 0018](adr/0018-reprogramacion-y-categorias-de-proveedor.md) · [Cronograma](09-gantt-y-planificacion.md)

Solicitud del 27/10/2026, HU-04/HU-08, BK-010/BK-022; CA-04.6/04.7 y CA-08.7/08.8. Ajuste local de M1/M3, sin iniciar M5/M6 ni publicar.

## Recorridos

En una ficha de obra, **Reprogramar [tarea]** permite elegir nuevo inicio conservando duración, ajustar último día/días de trabajo o desplazar fechas un número positivo/negativo de días calendario. Los hitos se mueven como una fecha única. **Preparar reprogramación** conserva un borrador; el motivo y **Guardar lote del cronograma** confirman la operación. Arrastre y resize del Gantt preparan el mismo lote. Las etapas agregan sus hijos; tareas retiradas conservan historia.

El Gantt adapta su altura a la cantidad de entregables vigentes, con mínimo 560 y máximo 1100 px, filas de 42 px y scroll interno. **Ampliar Gantt** abre un diálogo con el espacio disponible de la ventana; Escape o **Cerrar vista ampliada** regresa al editor y conserva cambios. El diálogo usa el manejo nativo de foco y teclado. Listado/formulario funcionan en móvil.

En **Clientes y proveedores → Proveedores → Registrar proveedor**, completar contacto, categoría principal y detalle opcional. No exige obra ni usuario; esos accesos se conceden después. El filtro de categoría conserva paginación y alcance autorizado. Categorías iniciales: Maestro de obras, Electricista, Plomero, Vidriero, Maquinaria, Acabados, Ferretería y Otros. Las fichas antiguas aparecen en Otros con su detalle libre intacto.

## Reglas, permisos y errores

- Planificación: GERENTE u OPERATIONS de la obra. Proveedor reporta su tarea, cliente solo recibe resumen y ADMIN carece de acceso al plan. Validación Spring del grafo completo, fechas, FS/lag y guard Project→Schedule; auditoría/snapshot en el mismo commit. Reprogramar invalida la cobertura aprobada de pesos sin borrar progreso previo ni modificar fechas contractuales.
- Un conflicto FS explica tarea, predecesora y fecha mínima; 422 no escribe el lote y conserva borrador. 412 bloquea sobrescritura hasta revisar y preparar de nuevo. No se mueve una sucesora silenciosamente.
- Cartera: GERENTE registra; asistente modifica contactos/categoría solo de proveedores de sus obras. Crear/clasificar/filtrar no concede cuenta, membresías, tareas ni documentos. No cambian las reglas de asignación de proveedores a tareas.
- Enum inválido: 400; categoría de proveedor aplicada a cliente: 422. Edición exige If-Match/idempotencia y usa la versión de la ficha; una versión obsoleta devuelve 412.

## Contrato, migración y activación

OpenAPI 0.4.3 agrega `SupplierCategory`, `PartyInput.supplierCategory`, `Party.supplierCategory` y filtro `GET /suppliers?supplierCategory=...`. Los campos son opcionales para consumidores anteriores. Crear sin categoría usa OTHER; editar sin ella conserva el valor. `category` sigue siendo detalle libre. El cronograma conserva su endpoint y fechas exclusivas; el formulario de reprogramación convierte último día inclusivo a `endExclusive`.

V11 agrega columna/constraint/índice, conserva el texto anterior, versiones y asignaciones. Respaldar antes de migrar el entorno habitual; reiniciar backend y recargar frontend de desarrollo o reconstruir/reiniciar standalone. No requiere configurar servicios externos nuevos. Una categoría principal está implementada; categorías múltiples, catálogo editable y asignación sin cuenta de usuario a tareas pertenecen a ampliaciones pendientes.

## Verificación

Resultados ejecutados el 27/10/2026, con fixtures sintéticos:

| Verificación | Resultado |
|---|---|
| Maven `verify`, PostgreSQL 18.6 y Java 27 | **125 pruebas pasan de 129 casos**; cuatro ensayos externos Drive/ClamAV omitidos. Incluye cinco casos nuevos de categorías/cartera/permisos y uno de upgrade V10→V11; regresión M0–M4 y cuentas |
| Navegador con respuestas controladas | **13 flujos pasan**: cinco nuevos (cartera/categoría, reprogramación, altura/ampliación/arrastre, 422 y 412), más ocho de membresías/creación de obras |
| OIDC/PostgreSQL reales aislados | **Dos flujos pasan**: regresión de alta directa/autorregistro/aprobación/suspensión, y proveedor sin obra + FS rechazado sin escritura + desplazamiento válido de ambas tareas + resize persistido después de recargar |
| Móvil, teclado y accesibilidad | Formularios a 390 px sin desbordamiento, guardado por teclado, cero violaciones Axe WCAG 2/2.1/2.2 AA en las vistas de cartera/reprogramación ensayadas. Diálogo de Gantt probado en móvil/desktop, Escape y retorno del foco |
| Espacio Gantt | 16 tareas visibles; captura revisada. Altura normal 792 px, diálogo de 1018 px en viewport de 1050 px. Arrastre conserva duración; resize cambia solo el extremo seleccionado |
| Migración/restore | Upgrade preserva detalles/versiones y rechaza categorías inválidas; copia AES-GCM/restauración a DB nueva: **62 tablas, 11 migraciones**, conteos/hashes integrales iguales. Conserva tres fichas de cartera, categoría, dos obras, dos tareas, dependencia FS y tres revisiones del cronograma, cuentas/permisos/activación/suspensión. **1,746 s**, excluyendo arranque/IdP/objetos |
| Frontend y contratos | ESLint, TypeScript estricto, aritmética de fechas/transportes Node y build standalone correctos. OpenAPI compatible con 0.4.2; checker conserva campos/requisitos previos y admite nuevos campos/filtros opcionales, con cuatro pruebas del checker |

Suites: `SupplierCategoryIntegrationTests`, `SupplierCategoryMigrationTests`, `M3IntegrationTests`, `tests/scheduling-dates.test.ts`, `tests/e2e/planning-improvements.spec.ts` y `tests/e2e/planning-real.spec.ts`. El entorno aislado conserva realm `ato-onboarding` en 8182, backend 9194, SMTP capturado 2527 y standalone 3302, como en [25](25-altas-y-aprobacion-de-cuentas.md). Preparar con `scripts/prepare-onboarding-test.py --frontend-only` y compilar antes de ejecutar los tests con `playwright.onboarding.config.ts`.

No se instalaron dependencias ni se modificó el entorno habitual. El build ensayado apunta al backend aislado; reconstruir con la configuración habitual antes de usar `npm run start` fuera del ensayo. CI remota, publicación y servicios externos reales no se ejecutaron. La copia no prueba recuperación de IdP/objetos ni RPO/RTO productivo; los checks automatizados no constituyen certificación de accesibilidad.
