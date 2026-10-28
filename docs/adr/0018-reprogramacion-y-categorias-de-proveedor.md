# ADR 0018 — Reprogramación y categorías de proveedor

Estado: aceptado para el ajuste local solicitado el 27/10/2026. HU-04/HU-08; BK-010/BK-022. [Entrega](../26-reprogramacion-y-proveedores.md).

## Contexto

El usuario solicita mover fechas y reagendar tareas, dar de alta proveedores antes de asignarles una obra, clasificarlos por categoría y aumentar el espacio útil del Gantt. El lote transaccional M3 ya guarda fechas; la cartera M1 ya permite proveedores independientes. La interfaz debe hacer evidentes ambos recorridos.

## Decisión

- Conservar Spring, PostgreSQL y SVAR Community. Reprogramar prepara el mismo lote `/schedule/changes`, con motivo, revisión, If-Match e idempotencia. Mover inicio conserva duración; se puede cambiar duración/último día, desplazar días positivos/negativos o mover un hito. Fechas civiles y fin exclusivo del contrato API permanecen intactos.
- Mantener validación global FS/lag/DAG, historial y revisión de pesos. Un rechazo conserva el borrador; una revisión obsoleta exige revisar datos. Las sucesoras no se desplazan automáticamente y el contrato firmado no se modifica.
- Dimensionar el Gantt con las filas visibles (560–1100 px), filas de 42 px y scroll para planes largos; una vista ampliada usa un diálogo nativo con Escape y retorno del foco.
- La cartera no requiere obra ni cuenta. Registrar/clasificar no crea accesos. GERENTE crea; asistente solo edita proveedores dentro de su alcance operativo vigente. ADMIN no adquiere acceso empresarial.
- Implementar una **categoría principal** como enum Java/contrato TypeScript: maestro de obras, electricista, plomero, vidriero, maquinaria, acabados, ferretería u otros. Es una decisión de implementación del catálogo inicial; las categorías múltiples del alcance completo HU-04 siguen pendientes. El texto libre anterior se conserva como especialidad/detalle.
- V11 agrega `supplier_category` con CHECK e índice; proveedores anteriores quedan OTHER sin adivinar especialidades. Categoría y contacto comparten versión/idempotencia/auditoría. El filtro se aplica antes de paginar y dentro del alcance autorizado. DTO/param nuevos son opcionales para clientes anteriores; omisión al crear implica OTHER y al editar conserva el valor vigente.

## Consecuencias

No se instalan bibliotecas ni se activa autoscheduling/funciones PRO. Cambiar el catálogo requiere actualización coordinada de enum, migración/constraint, contrato y etiquetas. La vista ampliada facilita trabajar con el cronograma completo, y el formulario conserva una alternativa de teclado/móvil. [SVAR documenta el evento update-task y su desplazamiento](https://docs.svar.dev/react/gantt/api/actions/update-task/); se conserva el adaptador probado de M3.
