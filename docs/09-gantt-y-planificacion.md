# 09 — Gantt y planificación

Actualización 27/10/2026: formulario **Reprogramar** con inicio/duración/último día y desplazamiento ±días; hitos de fecha única. Prepara el lote M3 con las mismas reglas RN-G01–G08. Gantt con altura según filas y diálogo ampliado. [Recorridos, errores y verificación](26-reprogramacion-y-proveedores.md); [ADR 0018](adr/0018-reprogramacion-y-categorias-de-proveedor.md).

[Índice](README.md) · [UX](07-diseno-ux-ui.md) · [Datos](05-modelo-de-datos.md) · [Calendar](10-documentos-e-integraciones.md)

## SVAR: capacidad comprobada y límites

Stack confirmado: SVAR React Gantt inicialmente. Consultadas fuentes oficiales el 7 de octubre de 2026; la documentación presenta línea 2.7. Community usa `@svar-ui/react-gantt` (MIT), PRO `@svar/react-gantt` (comercial); `wx-react-gantt` está deprecado. [Instalación y nombres de paquetes](https://docs.svar.dev/react/gantt/getting-started/installation/).

| Capacidad del componente | Community / PRO según documentación | Diseño ATO |
|---|---|---|
| Tareas, etapas, hitos, jerarquía | Community | Persistencia/validación propia |
| Edición, drag/resize, progreso, dependencias FS/SS/FF/SF | Community | FS en MVP; otras reglas en Spring si se amplía |
| Zoom, escalas, filtros, readonly, localización | Community | Desktop; agenda móvil propia |
| Cálculo automático de resúmenes | PRO | Agregación/ponderación ATO en backend |
| Auto-scheduling, ruta crítica, baselines y calendarios laborales | PRO | No prometer en MVP; baseline de pesos propio no es la baseline visual comercial |
| Recursos, carga de trabajo y calendarios por recurso | PRO | Asignación de proveedores mediante selector/DTO propios; sin panel de recursos PRO |
| Undo/redo y exportaciones PDF/PNG/Excel/MS Project | PRO | No incluir como Community; reportes propios no son export del Gantt |

Fuentes: [overview oficial](https://docs.svar.dev/react/gantt/overview/) y [README del proyecto](https://github.com/svar-widgets/react-gantt/blob/main/readme.md). Tener dependencias editables en UI no autoriza publicar un plan inválido. El manifiesto declara React ≥18; probar React 19, hidratación, Client Component y CSS junto a shadcn antes de fijar versión. [Manifest y licencia](https://github.com/svar-widgets/react-gantt/blob/main/package.json).

## Modelo y reglas canónicas

- **RN-G01 — Tiempo:** fechas civiles Costa Rica, intervalos de tarea hoja `[startDate,endExclusive)`, duración positiva en días calendario. Etapa agrega min/max de hijos y no se edita como hoja. Hito tiene una fecha y duración cero; no adquiere avance por transcurso de días. UI puede presentar último día inclusivo, adaptador convierte sin perder un día.
- **RN-G02 — Estructura:** una tarea tiene un padre de la misma obra; sin ciclos de jerarquía, IDs únicos y sin dependencia directa a sí misma. Una etapa no recibe peso adicional al de sus hojas. Eliminación de tarea con avance/evidencia produce retiro/versionado, no borra historia.
- **RN-G03 — Dependencias:** grafo dirigido acíclico de tareas programables. MVP admite FS y lag no negativo en días calendario. `start(sucesora) ≥ endExclusive(predecesora) + lag`; cambios que incumplan se rechazan con explicación. No suponer reprogramación automática gratis.
- **RN-G04 — Asignación:** proveedor activo/asignación vigente; asignar no concede datos del proyecto fuera del DTO autorizado. Varias asignaciones no duplican progreso/peso. Una tarea de extra pertenece al mismo proyecto y solo entra al alcance contractual después de aprobación.
- **RN-G05 — Concurrencia:** cronograma completo tiene `revision`; cualquier mutación de tarea, parent, vínculo, fecha o baseline bloquea primero Project para comprobar que admite edición y luego Schedule usando esa revisión. Lectura/validación/escritura del grafo ocurre en la misma transacción. Cierre adquiere el mismo guard de Project; revisión obsoleta→412 y revisión humana, no overwrite ni retry ciego.
- **RN-G06 — Avance:** reportado `0..100` separado del verificado por GERENTE; fecha de corte y evidencia. Proveedor/asistente reportan; cliente ve únicamente el valor verificado y alcance aprobado.
- **RN-G07 — Ponderación:** baseline aprobada, pesos positivos exactos sobre hojas/hitos de alcance entregable; sumatoria normalizada =1 (o 100%). Tareas administrativas internas peso cero y se excluyen de denominador; exclusión explícita/revisada. Porcentaje solo si toda cobertura relevante está ponderada.
- **RN-G08 — Atrasos y cambio de alcance:** comparar corte contra baseline/fechas estimadas y avance verificado, no usar color como única señal. Cambiar fechas/pesos o agregar extras requiere revisión histórica; recalcular y explicar cambios del porcentaje, sin forzar monotonía falsa.

Estados de tarea propuestos: PLANNED, IN_PROGRESS, BLOCKED, COMPLETED, CANCELLED. COMPLETED requiere progreso verificado 100 y evidencia aplicable; reabrir exige motivo. Suspender obra no marca tareas completas. Fechas estimadas son distintas de compromiso contractual: desplazar tarea no modifica el plazo firmado automáticamente.

## Validación del DAG y calendario

Por comando/lote: autorizar obra y todas las tareas/proveedores; lock/versionar Schedule; aplicar delta en memoria; validar árbol; construir adjacency de dependencias; ejecutar Kahn o DFS con detección de ciclo `O(V+E)`; validar intervalos y lags; guardar lote y auditoría/outbox en commit. Límite de cantidad/longitud de batch evita consumo ilimitado. Validar DAG global del proyecto aunque se edite una tarea filtrada.

FS inicial se calcula sobre fin exclusivo. Si se agregan SS/FF/SF: `start(B)≥start(A)+lag`, `end(B)≥end(A)+lag`, `end(B)≥start(A)+lag`, respectivamente; se definen pruebas antes de habilitarlas. Lead negativo, calendarios hábiles y feriados requieren política P-12 y revisión de algoritmo, no conversión implícita de “un día” a 24 horas en UTC.

No se moverá sucesora silenciosamente: el servidor devuelve conflictos y propuesta de replanificación para aceptación. Autoschedule/ruta crítica/carga de recursos se evalúan como ampliación con licencia o algoritmo explícito, sin inventar disponibilidad Community.

## Metodología de avance físico propuesta

Preferir pesos por entregables/cantidades físicas aprobadas; alternativamente esfuerzo planificado si el negocio lo valida. Evitar media de tareas, duración transcurrida o porcentaje financiero: crear diez tareas pequeñas no debe cambiar el peso del entregable. Costo estimado puede orientar un peso solo cuando se justifique, nunca porque sea más fácil de sumar.

`avance = 100 × Σ(w_i × p_i/100)`, con `Σw_i=1`. Hojas con medición física: `p_i = min(100, cantidadVerificada / cantidadPlanificada ×100)`; cambios de cantidad requieren baseline revisada. Hitos: `p_i ∈ {0,100}` según evidencia de aceptación, no fecha. Grupos muestran agregado de hojas y no entran de nuevo en la suma.

Ejemplo sintético: estructura 50% del peso, avance 80%; instalaciones 30%, avance 20%; acabados 20%, avance 20%: `0.5×80 + 0.3×20 + 0.2×20 = 50%`. Si una etapa además muestra 50%, no se cuenta como cuarta tarea. Un hito de entrega ponderado requiere reservar parte de los pesos, no añadirlo con peso extra a una baseline que ya suma 100.

Hasta aprobar baseline/cobertura: mostrar “Avance pendiente de definición”, no 0% engañoso. Si hay avance no verificado, mostrarlo solo a operación con señal de revisión. El resumen cliente incluye revisión, última verificación, hitos autorizados y fecha estimada; no datos de proveedores, costos, dependencias internas ni Gantt administrativo.

Agregar extra: aprobar alcance contractual, definir tareas/pesos y nueva baseline; conservar snapshot previo. Si el avance baja de 60% a 54% por mayor alcance, informar ambos cortes y causa; no editar progreso de tareas existentes para conservar visualmente 60%.

## Integración UI, guardado y pruebas

Adaptador `scheduling` traduce UUID/fechas/estado/revisión a modelo SVAR; el componente no se serializa directamente a DB. Cambios locales se muestran como pendientes; save por lote atómico, deshacer local antes de commit y refrescar revisión aceptada. No asumir WebSocket; TanStack Query invalida schedule/resumen tras guardar. Si se implementa autosave, mismas precondiciones y debounce sin perder orden.

El servidor no persiste fechas arbitrarias de resumen calculadas por UI; calcula min/max, avance y retraso oficial. Móvil usa lista/agenda y formulario. Calendar consume eventos tras commit, y su error no cambia estado del cronograma. T-GANTT/T-CONCUR/T-UI cubren ciclos largos, dos inserciones concurrentes que juntas producirían ciclo, FS en fin exclusivo, hitos, baseline incompleta y cambio de alcance.

## Aplicación M3

[Entrega ejecutada](20-entrega-m3.md) · [ADR 0013](adr/0013-planificacion-y-portal-m3.md) · [OpenAPI M3](openapi/m3.json). P-12 usa días calendario/FS y pesos físicos explícitos que suman 100, aprobados por GERENTE. Fechas civiles, lags, árbol y DAG se validan en Spring; formularios/Gantt preparan el mismo lote. Los IDs/tipo/extra conservan identidad y las tareas se retiran con historia. Máximo 500 tareas conservadas y 2000 dependencias.

Reportes y verificaciones son hechos separados con evidencia limpia. Correcciones requieren otra medición y motivo; las etapas agregan descendientes y no tienen peso propio. Sin cobertura aprobada no se publica porcentaje ni un hito propuesto. Cambios contractuales generan snapshots síncronos en la misma transacción; el resumen al corte no reutiliza datos vivos. Alternativas laborales, medición por cantidades automática y PRO quedan pendientes.
