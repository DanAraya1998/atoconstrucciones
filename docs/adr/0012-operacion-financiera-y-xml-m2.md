# 0012 — Operación financiera y XML M2

**Estado:** Implementado para desarrollo local, dentro de M2 autorizado. **Fecha:** 8 de octubre de 2026. **HU:** HU-02/HU-03/HU-07/HU-12. **BK:** BK-016 a BK-021. [Reglas](../08-reglas-financieras.md) · [Entrega](../19-entrega-m2.md).

## Decisión

Desarrollar la parte MVP del modelo de [ADR 0005](0005-hechos-financieros.md) conservando Java 27, BOM Boot, monolito modular y Google Drive. Nuevos módulos `finance` (políticas comunes), `treasury`, `costs`, `documents.fiscal` y `reporting`; servicios transaccionales con JDBC, patrón ya utilizado en M0/M1. Ninguna dependencia nueva de aplicación.

Política inicial conservadora: GERENTE confirma ingresos/pagos, valida costos, autoriza excepciones, reversiones y cuentas lógicas con contraseña reciente. Asistente prepara/revisa/corrige propuestas en obras OPERATIONS. CLIENTE reporta y consulta cobros de su obra. ADMIN y PROVEEDOR no reciben capacidades financieras de M2. Esta es la configuración operativa adoptada al implementar el hito; no se atribuye al usuario una decisión fiscal adicional ni aprobación de deducibilidad.

CRC, importes operativos brutos e impuestos separados. Tesorería hasta dos decimales, fuentes fiscales/costos hasta cinco; sin redondeo silencioso de imputaciones. P-06/USD y presupuestos/compromisos permanecen en M5. Una propuesta de gasto se corrige con versión e imputaciones nuevas; revisiones anteriores permanecen inmutables. Costo y caja confirmados se corrigen con eventos inversos vinculados.

## Hechos y concurrencia

`cost_recognition`, `treasury_effect`, `collection_application` y `payment_application` conservan los hechos. Confirmar ingreso no duplica un depósito reportado; vincular un duplicado no añade una imputación. La identidad bancaria/manual se declara como `accountId + factKey`, protegida por índice único y lock transaccional. La referencia del comprobante solo genera alerta, conforme CA-02.4; el identificador de banco no se inventa ni se extrae de un formato todavía no soportado.

Toda mutación bloquea primero las obras afectadas en orden de UUID, vuelve a comprobar alcance/estado y después toma los agregados. Clave idempotente, ETag, evidencia/excepción y auditoría pertenecen a la misma transacción. Un fallo de auditoría revierte también el efecto y la clave. Confirmados no admiten cambios de importes, borrado ni TRUNCATE; reversiones de gastos conservan pagos y saldo a favor. Las transferencias tienen un par atómico y se excluyen de ingresos de clientes y costos.

## Parser y reportes

XSD oficiales almacenados con bytes originales, URLs y SHA-256; dependencias resueltas exclusivamente desde recursos locales. La DTD del XSD W3C de firma se retira únicamente en memoria tras comprobar el hash del recurso confiable: no contiene usos de entidades. La política para documentos del usuario continúa prohibiendo DTD/XXE/XInclude y cualquier resolución remota. La declaración `vc:minVersion=1.1` de Hacienda no introduce construcciones XSD 1.1 en estos archivos; los cinco esquemas se compilaron y probaron con el validador JDK.

Extracción en JVM hija con memoria, tiempo y salida acotados, también desde el JAR Boot. Firma y aceptación tributaria quedan explícitamente **sin verificar**. NC/ND requieren una referencia única, original importado y emisor/receptor compatibles; se validan sus imputaciones antes de reconocer el ajuste. La NC no crea una devolución bancaria.

Reportes con consulta agrupada y snapshot transaccional REPEATABLE READ, moneda/base/corte/versión de fórmula y proyección cliente independiente. Historial de estados y revisiones conserva pendientes a cortes anteriores. Sin presupuesto se informa ausencia de base; sin compromisos M5, fondos tras obligaciones conocidas no prometen liquidez libre de todos los costos futuros.

## Verificación y alternativas

Actualizar un saldo mutable o un flag `paid` pierde la explicación de anticipos/NC/reversiones. Resolver XSD desde el XML recibido introduce acceso remoto; declarar aceptación fiscal al pasar XSD confunde controles independientes. No se implementa contabilidad completa, banca ejecutora ni emisión tributaria.

Pruebas de PostgreSQL, concurrencia, permisos, XML hostil, upgrade, navegador y restore en [19](../19-entrega-m2.md). El harness de navegador vive exclusivamente en `src/test`; sus adaptadores controlados no se incluyen en el JAR y no acreditan una conexión Drive real.
