# 19 — Entrega M2

[Índice](README.md) · [Backlog](13-roadmap-y-backlog.md) · [Contrato M0–M2](openapi/m2.json) · [ADR 0012](adr/0012-operacion-financiera-y-xml-m2.md) · [Arranque](../README.md)

El usuario autorizó implementar M2 el 8 de octubre de 2026. BK-016 a BK-021 están implementadas y verificadas para desarrollo local. Se conserva el trabajo M0/M1, el stack y Google Drive; M3–M6 siguen pendientes.

## Capacidades entregadas

| Tarea | Comportamiento implementado | Alcance |
|---|---|---|
| BK-016 | Reporte/revisión/rechazo, confirmación única, vínculo de duplicados, cobros parciales, exceso y reversión | CLIENTE reporta su obra; GERENTE confirma con evidencia limpia o excepción motivada. Reportado no cambia caja/deuda |
| BK-017 | Gastos/categorías/base/impuestos, distribución por obra/extra, revisión y corrección con ETag, validación y reversión | Las revisiones/imputaciones anteriores se conservan; validar reconoce costo sin pagar. Porciones no imputadas requieren motivo |
| BK-018 | Cuentas lógicas, propuestas de movimiento, pagos parciales, anticipos, aplicaciones, reembolsos/devoluciones y correcciones | Aplicar anticipo no repite salida. Transferencias internas crean un par atómico. Nunca ejecuta pagos bancarios |
| BK-019 | Resúmenes por obra, proyección cliente, consolidado GERENTE y seguimiento operativo por obra | CRC/bruto/corte/fórmula, deuda y créditos separados; presupuesto ausente explícito y compromisos pendientes M5 |
| BK-020 | Bundle oficial local, hashes, validación XSD y extracción acotada | Factura, tiquete, factura de compra, NC y ND 4.4. Firma/aceptación tributaria sin verificar; otros tipos/versiones requieren soporte posterior |
| BK-021 | Propuesta fiscal, identidad única XML/PDF/imagen, diferencias, NC/ND vinculadas | Importar no crea costo/caja; NC reduce costo aprobado una vez y conserva pago hasta devolución real |

Pantallas: operación financiera en `/obras/{id}` y `/finanzas` para GERENTE/asistente según alcance. Formularios seleccionan cuentas/proveedores/obras/evidencias y admiten imputaciones múltiples; importes permanecen strings exactos. El cliente recibe su resumen/cobros sin costos, pagos ni márgenes. Las aprobaciones conservan la reautenticación de contraseña de cinco minutos.

## Políticas y límites operativos

Se adoptaron los valores conservadores propuestos para P-03/P-05: asistente prepara/revisa, GERENTE decide; bruto con impuestos desglosados, sin asumir IVA recuperable. Gastos sin proveedor necesitan justificación; validar sin evidencia necesita excepción motivada de GERENTE. La revisión fiscal productiva y la identidad legal definitiva del receptor siguen pendientes: el XML muestra emisor/receptor para cotejo humano y exige revisión al validar.

El identificador `factKey` corresponde al hecho verificado dentro de una cuenta lógica; el gerente debe conservarlo al vincular reportes del mismo movimiento. Referencias coincidentes alertan y no bloquean automáticamente. No existen adaptadores bancarios hasta BK-039. Las correcciones ordinarias de caja admiten reversión parcial de movimientos fuente; devoluciones y pares de transferencia se conservan como hechos vinculados y no se revierten individualmente por ese comando.

Para XML se preservan originales, líneas con detalle fiscal, totales, moneda/tasa y hashes. La tolerancia local de cotejo de sumas es 0.00001: una diferencia mayor requiere revisión, sin crear gasto; no se presenta esta tolerancia como certificación tributaria. No se valida firma digital, deducibilidad, aceptación de Hacienda ni compatibilidad fiscal histórica. NC/ND con varias referencias requieren revisión y soporte posterior.

No existe presupuesto ni compromiso empresarial implementado en M2: se muestra “sin base presupuestaria”. El motor de fórmulas reproduce el ejemplo A completo y la reserva de B con entradas sintéticas; los reportes persistidos operan con los hechos MVP y no fabrican CP/QA/CR de M5. `freeAfterKnownObligations` descuenta deuda validada y no promete cubrir costos futuros todavía no registrados. Sobrepagos/créditos no se compensan entre obras.

Evidencias de costos/fiscal/caja requieren audiencia interna y propietario operativo, y no pueden compartirse luego con clientes/proveedores. Los comprobantes que aporta el cliente al reportar un depósito conservan su acceso propio. Revocar membresía de obra/parte impide lecturas posteriores. ADMIN no recibe balances globales ni acceso funcional financiero.

## Contrato y datos

V6 amplía V1–V5 sin editar sus migraciones. Tablas de tesorería, propuestas/revisiones e imputaciones, reconocimientos, fuentes fiscales y eventos de estados. Hechos/revisiones inmutables; fuentes aprobadas protegidas. Transacciones de comandos incluyen idempotencia, ETag, locks, evidencias y auditoría.

`m2.json` conserva todos los contratos M0/M1 y añade los recursos implementados. Generador TS y `/api/v1/openapi.json` utilizan M2. Lecturas paginadas máximo 100, por obra y permiso; el consolidado es exclusivamente GERENTE. `PATCH /expenses/{id}` corrige solo propuestas sin validar; no edita costo confirmado. `asOf` informa tiempo de registro y fecha civil efectiva en America/Costa_Rica. El corte no recalcula hechos con valores vivos.

## Bundle oficial y dependencias

No se añadieron librerías. JDK XML/JVM hija y Jackson del BOM existente. [Manifest local](../src/main/resources/fiscal/v4.4/manifest.json) registra URLs/hashes de los cinco XSD Hacienda y la dependencia W3C. Descargados el 8 de octubre de 2026 desde Hacienda, por ejemplo [factura 4.4](https://www.hacienda.go.cr/docs/FacturaElectronica_V4.4.xsd.xml), [nota de crédito](https://www.hacienda.go.cr/docs/NotaCreditoElectronica_V4.4.xsd.xml) y [nota de débito](https://www.hacienda.go.cr/docs/NotaDebitoElectronica_V4.4.xsd.xml); firma desde [W3C](https://www.w3.org/TR/2002/REC-xmldsig-core-20020212/xmldsig-core-schema.xsd), conservando su aviso de licencia.

El catálogo PDF previamente citado respondió 404 al descargarlo en esta sesión; sí se obtuvieron y compilaron los archivos oficiales individuales. El manifest identifica exactamente la revisión por hash; no se atribuye una fecha de revisión oficial ausente de la publicación. Revisiones futuras se incorporarán con nuevos fixtures/manifest y evaluación de compatibilidad.

## Verificación ejecutada

Resultados del 8 de octubre de 2026, con datos sintéticos:

| Verificación | Resultado |
|---|---|
| `./mvnw --batch-mode --no-transfer-progress clean verify` | 70 casos: **66 ejecutados**, 0 fallos/errores; 4 omitidos opt-in (ClamAV y Drive reales). JAR empaquetado |
| Negocio M2 / PostgreSQL real | 18 pruebas: cálculos, ocho confirmaciones concurrentes, hecho duplicado, límites simultáneos de obligación, factura distribuida, revisión/ETag, anticipos, NC/ND/reembolso, reversión, corte, permisos/revocación y rollback de auditoría |
| Parser y fórmulas | Cinco tipos válidos, precisión, JVM hija, hint XSD remoto ignorado, DTD/XXE/bomba/profundidad/namespace/estructura/totales inválidos; ejemplo A y reserva de B |
| Upgrade | V5→V6 conserva identidad, contrato y revisión Drive; validación Flyway. Suites históricas prueban sus targets V2→V5/V4→V5 |
| Rendimiento local | 200 obras/10 000 gastos; 9 muestras, p95 del servicio agrupado **131 ms**. Laboratorio local sin HTTP/concurrencia de usuarios; no acredita presupuesto en staging |
| Parser en JAR Boot | Factura de fixture validada/extraída con bundle local y diagnósticos de firma/estado sin verificar |
| Frontend | Generación M2, lint, TypeScript, transporte y build Next; sin dependencias nuevas |
| Navegador | **6 pruebas** correctas: tres de fundamentos, OIDC real y dos M2. M1 real omitida al faltar Drive. M2 recorre reporte→confirmación→corrección/validación→anticipo/aplicación y XML→costo→NC; móvil, teclado y proyección cliente |
| Restore PostgreSQL | Dump/restore de la DB aislada de navegador en una base nueva; comparación de **21 tablas**, incluyendo tres gastos, cuatro revisiones/imputaciones, tres reconocimientos, anticipo/aplicación y NC, además de seis migraciones. Objetos Drive e IdP quedan fuera de este ensayo |
| Contratos/documentación | Compatibilidad con M1, enlaces/secretos y pruebas de fallos controlados; contrato empaquetado igual a fuente |

El navegador usa servicios financieros y PostgreSQL/Keycloak reales. Almacenamiento/antivirus son adaptadores controlados **solo de test**, activados explícitamente por `M2BrowserHarness` y ausentes del JAR. La validación Drive/antivirus real continúa en sus suites opt-in; no se afirma haber conectado la cuenta del cliente. Capturas/medidas/dumps están excluidos de Git en `frontend/test-results/` y `target/`.

## Reproducir el ensayo de navegador aislado

Con Keycloak local y `.m0-local/accounts.txt`, preparar classpath y ejecutar el harness en otra terminal:

```bash
./mvnw --batch-mode --no-transfer-progress test-compile dependency:build-classpath -Dmdep.outputFile=target/m2-test-classpath.txt
python3 scripts/run-m2-browser.py
```

El harness crea PostgreSQL temporal en un puerto libre y Spring en 9192. Se requiere opt-in interno de prueba; no utiliza una DB empresarial ni el Drive operativo. En la terminal del frontend, con 3000 libre:

```bash
ATO_BACKEND_ORIGIN=http://127.0.0.1:9192 npm run build --prefix frontend
ATO_E2E_M2=1 ATO_E2E_KEYCLOAK=1 npm run test:e2e --prefix frontend
python3 scripts/verify-m2-restore.py
```

Ejecutar restore después del navegador, sin escrituras simultáneas. Detener el harness con Ctrl+C y recompilar frontend sin `ATO_BACKEND_ORIGIN` para volver a Spring en 8080. No ejecutar el harness como servicio de aplicación ni interpretar sus adaptadores como almacenamiento productivo.

## Pendientes

Drive real/carpeta/credenciales/restore de objetos y políticas de privacidad siguen en [18](18-almacenamiento-google-drive.md) y M1. Boot estable, revisión fiscal/privacidad y operación productiva siguen pendientes. M3–M6, USD/conversión, compromisos/presupuestos, bancos, cierre, PDF, personal, Calendar y despliegue no forman parte de esta entrega. CI incluye backend M2 y su harness de navegador; checks equivalentes ejecutados localmente; ejecución remota pendiente.
