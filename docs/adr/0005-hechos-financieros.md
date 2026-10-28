# 0005 — Hechos financieros, imputaciones y reversiones

**Estado:** Propuesto para el modelo; distinciones financieras requeridas por pedido. **Fecha:** 7 de octubre de 2026. **HU:** HU-01–HU-07/HU-10. **BK:** BK-014–BK-021/BK-031/BK-037. **Pendientes:** P-03/P-05/P-06/P-07. [Reglas canónicas](../08-reglas-financieras.md).

## Contexto y propuesta

Un saldo genérico no distingue contrato, caja, costo y compromiso. Usar contrato base+enmiendas, reporte separado de ingreso, reconocimiento de costo separado de pago, imputaciones parciales y eventos efectivos/reversiones vinculados. BigDecimal/numeric, moneda y tasa histórica cuando aplique. Preservar fuente/documentos y calcular métricas por corte.

No implementar plan contable/partida doble completo ahora. El historial por agregado es append-only sobre hechos aprobados, no event sourcing universal; proyecciones y constraints verificables en PostgreSQL. Separación personal por tablas/esquema y API.

## Alternativas y consecuencias

Editar totales/balances almacenados sacrifica explicación y duplica importe cuando llega banco/XML. Contabilidad completa excede historias/costo operativo. El modelo propuesto conserva trazabilidad y permite crecer, pero exige diseño de locks/idempotencia y reconciliación de proyecciones; IVA recuperable/recognition no se inventa.

## Verificación

Resolver autoridad/base fiscal/cambio/evidencia contractual por tarea. T-FIN/T-CASH/T-CONCUR/T-IDEMP reproducen ejemplos A–F y anticipos sin doble caja/reserva. Las futuras ampliaciones contables tendrán mapeo explícito y ADR propio.

## Aplicación M2

Implementación MVP autorizada en [ADR 0012](0012-operacion-financiera-y-xml-m2.md). Presupuestos/compromisos, conversión y cierre del modelo completo permanecen pendientes. [Entrega y límites](../19-entrega-m2.md).
