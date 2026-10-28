# 08 — Reglas financieras

[Índice](README.md) · [Datos](05-modelo-de-datos.md) · [Permisos](03-roles-y-permisos.md) · [Documentos](10-documentos-e-integraciones.md)

## Alcance y unidad de cálculo

Modelo de control financiero operativo, no libro mayor contable ni declaración tributaria. Las fórmulas siguientes son propuestas de diseño; exclusividad de finanzas globales/personales, distinción reportado/confirmado y extras aprobados sí son requisitos confirmados. P-03, P-05, P-06 y P-07 resuelven aprobadores, impuestos, cambio y validez contractual.

Toda cifra debe indicar **proyecto/ámbito, moneda, fecha de corte, base fiscal y estado considerado**. Base operativa inicial propuesta: bruto, impuestos desglosados, sin asumir IVA recuperable. Comparaciones en neto pueden añadirse solo con criterio fiscal documentado. No sumar CRC+USD ni presupuestos netos con gastos brutos. Los datos fuente XML mantienen sus cinco decimales permitidos donde corresponda; display no altera precisión.

## Catálogo de indicadores y fórmulas

Para una obra, moneda contractual `c` y corte `t`, excluir borradores/rechazos y considerar efectos/reversiones efectivos a `t`.

| Concepto | Definición / fórmula | No representa |
|---|---|---|
| 1. Monto contractual inicial `CI` | Total de la revisión base formalizada, con base fiscal declarada | Cobro, presupuesto o valor editable después de activar |
| 2. Extras aprobados netos `EA` | Suma de enmiendas efectivas provenientes de extras aprobados, menos sus cancelaciones/reducciones autorizadas | Solicitudes, cotizaciones ni extras pendientes |
| 3. Monto contractual vigente `CV` | `CI + EA + AO`, donde `AO` son otras enmiendas autorizadas no originadas en extras; cada enmienda se cuenta una vez | Precio original + copia completa del contrato revisado |
| 4. Depósitos reportados `DR` | Suma de reportes en REPORTADO/EN_REVISION, señalando duplicados bajo revisión. Indicador informativo, no efectivo | Ingreso confirmado o reducción de saldo |
| 5. Ingresos confirmados de clientes `IC` | Caja recibida y atribuida a la obra, neta de devoluciones y reversiones. Reportar bruto/devoluciones/neto por separado. Transferencias internas/aportes no son ingreso de cliente | Valor de contrato, ingreso contable devengado o saldo banco completo |
| Cobros aplicados `CA` | Porción de `IC` aplicada al contrato en moneda contractual con tasa fijada; exceso no aplicado queda como crédito/pendiente de aplicar | Repetición del depósito reportado o de la línea bancaria |
| 6. Costos presupuestados `CP` | Suma de partidas de presupuesto aprobado vigente, misma moneda/base; versiones previas no se agregan | Cotizaciones aprobadas más gastos realizados |
| 7. Cotizaciones aprobadas `QA` | Importe vigente aprobado a proveedores, con sus enmiendas netas; es volumen autorizado | Gasto validado, pago o precio del extra al cliente |
| 8. Costos comprometidos restantes `CR` | Por compromiso `k`: `max(0, autorizado_k - consumoReconocido_k - liberacion_k)`; sumar remanentes. Consumo vincula costos validados una sola vez | Sumar todo QA nuevamente a costos ya devengados |
| 9. Gastos registrados `GR` | Importes propuestos en estados REGISTRADO/EN_REVISION; métricas separadas de validados. Vista “registrados totales” debe rotular si incluye estados validados | Deuda aprobada o salida de caja |
| 10. Gastos/costos validados `GV` | Sumatoria firmada de `CostRecognition` efectivo (validaciones menos créditos/reversiones). Imputación por obra, no total duplicado de facturas compartidas | Pagos ni consumo de presupuesto añadido otra vez |
| 11. Pagos realizados netos `PR` | Salidas confirmadas de caja atribuibles a proveedores/costos de la obra, incluidos anticipos, menos reembolsos y reversiones. Pago aplicado a gasto es otro indicador `PA` | Gasto devengado ni orden de pago no ejecutada |
| 12. Saldo de contrato firmado `SC` | `CV - CA`; pendiente de cobro `PC = max(0, SC)`; crédito/sobrepago aplicado `CC = max(0, -SC)`; exceso sin aplicación se muestra aparte | Fondos disponibles o beneficio de la obra |
| 13. Fondos de caja atribuibles `FD` | `IC + AF + TI - PR - TO - OC`, con aportes financieros `AF`, transferencias de asignación de fondos `TI/TO` y otras salidas confirmadas de caja de obra `OC`, separados de costos | Saldo bancario real completo ni dinero libre de compromisos |
| Fondos libres después de obligaciones `FL` | `FD - deudaValidadaPendiente - compromisoDeCajaSinCubrir`, mostrando componentes y un posible valor negativo. Por compromiso, reservar `max(0, CR_k - anticipoNoAplicado_k)` | Liquidez bancaria garantizada; un anticipo ya pagado no se reserva por segunda vez |
| 14. Resultado económico contractual estimado `RE` | `CV - GV`; resultado final operativo solo tras cierre y resolución de costos pendientes. Proyección al completar `RPE = CV - (GV + CR + costoFuturoNoComprometido)` | Utilidad contable/fiscal o ingreso efectivamente devengado por avance |

`IC` y `CA` pueden diferir por exceso, partidas no aplicadas o conversión/retención justificada. El cliente ve contrato/cobros aplicados/deuda/crédito; gerente además ve caja y costo. Resultado reconocido por porcentaje de avance requeriría política de reconocimiento de ingresos nueva; no derivarlo multiplicando contrato por progreso del Gantt.

Deuda validada pendiente `DVP = sum(max(0, costoReconocidoNeto_i - pagosAplicadosNetos_i - anticiposAplicados_i))`, calculada por obligación y moneda. Anticipo no aplicado se informa como derecho frente al proveedor, no se resta otra vez de caja cuando se aplica. `pagosAplicadosNetos` excluye las aplicaciones de anticipos contabilizadas en `anticiposAplicados`. La reserva de caja deduce anticipos todavía no aplicados del remanente del mismo compromiso; no permite compensarlos contra compromisos ajenos. Sobrepago tras nota de crédito es saldo a favor frente al proveedor; no “deuda negativa” ocultada.

## Reglas transversales canónicas

- **RN-F01 — Contrato y extras:** CI es inmutable después de formalizar; CV se deriva de enmiendas efectivas. Aprobar extra genera enmienda única por revisión; ninguna cotización de costo incrementa CV automáticamente. `CV ≥ 0`; reducción necesita evidencia y revisión de cobros/costos ya ocurridos.
- **RN-F02 — Reporte:** reporte es alegación/evidencia. Monto positivo, fecha/medio/referencia/actor; no modifica caja ni contrato. Distintos reportes de un mismo banco se pueden marcar DUPLICADO sin borrar historia.
- **RN-F03 — Confirmación:** GERENTE confirma movimiento real y su imputación, con evidencia. Una línea conciliada, un comprobante y un reporte pueden apuntar al mismo hecho; no crean tres ingresos. Idempotencia + unicidad del hecho + lock de imputación son obligatorios.
- **RN-F04 — Costo:** validación crea reconocimiento devengado por proyecto/partida. Nota fiscal/importación genera propuesta hasta revisión. Las imputaciones de todas las obras/ámbitos autorizados no exceden importe fiscal o diferencia manual explícita justificada; no asumir total factura = gasto de una obra.
- **RN-F05 — Pago/anticipo:** caja solo cambia por movimiento confirmado. Relación many-to-many entre pago y obligación, sumas acotadas, saldo residual. Anticipo contra compromiso/proveedor se aplica después al gasto sin nueva caja; devolución es movimiento inverso real, no borrar anticipo.
- **RN-F06 — Presupuesto/compromiso:** presupuesto expresa plan; QA expresa autorización proveedor; CR es remanente no reconocido. Reconocimiento reduce CR y aumenta GV sin sumar ambos como costo duplicado. Costos sin compromiso siguen en GV y disparan desviación/justificación.
- **RN-F07 — Desviaciones:** `desviacionDevengada = GV - CP`; `desviacionProyectada = GV + CR + costoFuturoNoComprometido - CP`. Porcentaje solo si CP>0; si no existe presupuesto mostrar “sin base”, nunca 0%. Alertas no aprueban sobrecostos.
- **RN-F08 — Crédito e impuestos:** NC referida al original ajusta costo/impuesto del ámbito imputado una vez, con aprobación. ND aumenta costo solo tras validación. IVA/tasas/exoneraciones se preservan según fuente y revisión fiscal; no inventar deducibilidad o fijar todas las facturas al 13%.
- **RN-F09 — Reversión/ajuste:** registro efectivo no se edita/elimina. Evento inverso vinculado, motivo, actor/instante y autorización; no revertir más de saldo reversible. Distinguir corrección de un hecho erróneo (sin movimiento real) de devolución real de efectivo.
- **RN-F10 — Cambios de alcance:** cancelación de extra ya aprobado genera enmienda; trabajo/costo incurrido no desaparece. Si cambia cronograma, se aprueba revisión de pesos/plazos independiente y se explica efecto de avance.
- **RN-F11 — Conciliación:** una decisión vincula hechos; no ejecuta pagos bancarios. Matching es propuesta; aprobación humana y corrección auditable. Enlace many-to-many solo hasta importe de línea/movimiento; no doble vínculo del mismo residual.
- **RN-F12 — Efectivo/transferencias:** caja requiere cuenta lógica, evidencia y responsable. Transferencia entre cuentas de empresa tiene par de movimientos vinculados y se excluye de ingresos/costos consolidados. Clasificación de banco no vuelve personal un gasto empresarial sin procedimiento.
- **RN-F13 — Separación personal:** registros/cuentas personales no forman parte de IC/GV/PR empresariales. Aportes/retiros de gerente se clasifican como financiación/distribución, no venta/costo de construcción; dos ámbitos enlazados solo con autorización titular y gerente.
- **RN-F14 — Monedas:** conversión por `ExchangeRateSnapshot`, fecha/fuente/dirección; original y convertido persistidos. Conversión por imputación no usa tasa viva para recalcular pasado. Totales por moneda si falta conversión válida.
- **RN-F15 — Precisión:** `BigDecimal`, escala/capacidad validadas y redondeo propuesto HALF_UP al liquidar CRC/USD a dos decimales; valor fiscal original hasta cinco. Redondeo por importe aplicado con residual explícito, no pérdida silenciosa; P-05/P-06 pueden ajustar política.
- **RN-F16 — Corte temporal:** fecha operación, devengo, confirmación y registro son distintas. Reporte declara criterio/corte; snapshot cerrado conserva hechos y versión de fórmula conocidos al cerrar. Registro tardío genera nueva revisión, no reescribe snapshot.
- **RN-F17 — Concurrencia:** toda mutación financiera de obra bloquea primero su Project y comprueba estado; luego contrato/obligación/compromiso/movimiento según un orden global fijo de tipos e IDs, además de versión. Si hay varias obras, bloquear sus UUID ordenados antes de los demás agregados. Cierre usa el mismo guard de Project, de modo que una aprobación concurrente no escapa al checklist. CERRADO exige reapertura/corrección autorizada. Un pre-check fuera de transacción no impide duplicados.
- **RN-F18 — Evidencia:** toda aprobación monetaria/cambio requiere documento limpio o motivo de excepción autorizado; auditar decisiones, antes/después y referencias; no loggear documentos completos.

## Estados y autoridad propuesta

| Agregado | Transiciones admitidas / consecuencia |
|---|---|
| Depósito | REPORTADO→EN_REVISION→CONFIRMADO/RECHAZADO/DUPLICADO. CONFIRMADO→REVERTIDO mediante hecho inverso. Cliente/asistente reportan; gerente decide P-03 |
| Gasto | BORRADOR→REGISTRADO→EN_REVISION→VALIDADO/RECHAZADO. Antes de validar puede corregirse con versión; después solo crédito/reversión. Pago tiene su propio estado |
| Pago/movimiento | BORRADOR→CONFIRMADO; REVERTIDO es resultado de corrección vinculada. No confirmar solo por factura, screenshot del proveedor o orden prevista |
| Cotización | BORRADOR→RECIBIDA→EN_REVISION→APROBADA/RECHAZADA; SUSTITUIDA conserva versión. Enmienda preserva consumo y remanente del compromiso |
| Extra | SOLICITADO→COTIZANDO→PENDIENTE_APROBACION→APROBADO→EN_EJECUCION→TERMINADO; RECHAZADO/CANCELADO con impacto contractual explícito si hubo aprobación |
| Conciliación | PROPUESTA→APROBADA/RECHAZADA; APROBADA→REVERTIDA con vínculo al cambio. No mutar línea bancaria fuente |

Motivos obligatorios en rechazo/reversión/ajuste/sobrecosto/aceptación excepcional; estado de avance, reconocimiento fiscal y pago no se reúnen en un único `status` de factura.

## Ejemplos sintéticos para pruebas

### A. Contrato, pendientes y caja

CRC bruto. `CI=10 000 000`; extra aprobado `1 000 000`, otro solicitado `800 000`; `AO=0`. `CV=11 000 000`, no `11 800 000`. Cobros confirmados/aplicados `4 000 000`; reportado adicional `500 000` pendiente: `PC=7 000 000`, `DR=500 000`.

`CP=6 000 000`, `QA=4 000 000`, costo validado consumiendo esos compromisos `2 500 000`; `CR=1 500 000`. Gastos registrados no validados `300 000` no aumentan GV. Pagos caja `2 000 000` aplicados íntegramente a esos costos: `GV=2 500 000`, `PR=PA=2 000 000`, `DVP=500 000`.

Sin aportes/transferencias/otras salidas: `FD=4 000 000-2 000 000=2 000 000`; `FL=2 000 000-500 000-1 500 000=0`. `RE=11 000 000-2 500 000=8 500 000` es estimado contractual, no utilidad realizada; si costo futuro no comprometido es `2 000 000`, `RPE=11 000 000-(2 500 000+1 500 000+2 000 000)=5 000 000`.

### B. Anticipo y pago parcial

Compromiso `1 000 000`. Anticipo confirmado `400 000`: `PR=400 000`, `GV=0`, `CR=1 000 000`, anticipo no aplicado `400 000`; la reserva de caja comprometida es `600 000`, no otro millón completo. Luego validar costo `600 000` y aplicar anticipo `400 000`: `GV=600 000`, `CR=400 000`, `DVP=200 000`, `PR` sigue `400 000`; reserva restante + deuda = `400 000+200 000=600 000`. Pagar restante `200 000` deja `PR=600 000`, `DVP=0`. **Aplicar anticipo no crea una segunda salida de 400 000.**

### C. Nota de crédito y devolución

Costo validado/pagado `1 130 000` (base `1 000 000`, impuesto de ejemplo `130 000`). NC de `113 000` con reparto proporcional autorizado: costo neto `1 017 000`. Caja pagada sigue `1 130 000` y hay saldo a favor del proveedor `113 000`. Cuando se confirma reembolso `113 000`, `PR=1 017 000`. La NC no es por sí misma ingreso bancario.

### D. Factura compartida, duplicados y sobrepago

Factura total `226 000`; imputaciones a A y B de `113 000` cada una: costo total reconocido `226 000`, no `452 000`. PDF e imagen de la misma clave solo son evidencias adicionales. Reimportar mismo XML no genera costo ni pago nuevo. Reporte y línea bancaria del mismo depósito `250 000` producen un único `IC=250 000`.

Contrato `1 000 000`, cobros aplicados `1 100 000`: `SC=-100 000`, `PC=0`, `CC=100 000`. Devolver exceso `100 000` baja IC/CA a `1 000 000` y CC a 0. Si se devuelve una parte contractual sin reducir CV mediante enmienda válida, PC reaparece: una devolución no elimina la obligación del cliente por sí sola.

### E. Conversión registrada

Contrato CRC; depósito USD `1 000.00` con tasa operativa fijada `510.0000000000 CRC/USD`: cobro contractual `510 000.00 CRC`; conservar `1 000 USD` y snapshot. Si hubo comisión bancaria CRC `2 000`, registrar salida/comisión separada: no cambiar cobro aplicado a `508 000` silenciosamente. Nueva tasa `520` no modifica el cobro histórico. Diferencia cambiaria solo como ajuste clasificado y aprobado; no se presenta como ingreso de construcción automático.

### F. Cancelación y cierre

CI `10 000 000`, extra aprobado `1 000 000`; cancelación acordada de `300 000` genera enmienda `-300 000`: `CV=10 700 000`. Costos ejecutados `200 000` de ese extra se mantienen. Un cierre con `PC=700 000`, gasto pendiente o compromiso abierto exige bloqueo/excepción explícita y snapshot de pendientes; nunca marcar cobrado/pagado por cerrar.

Cada ejemplo deberá convertirse en caso T-FIN/T-CASH/T-XML/T-CLOSE según [12](12-pruebas-y-calidad.md), incluyendo cortes temporales, doble envío y concurrencia.

## Implementación contractual M1

CRC inicial confirmado. Neto e impuestos manuales explícitos, máximo dos decimales; bruto = neto + impuestos. No se aplica porcentaje fiscal implícito. RN-F01/RN-F10/RN-F18: base formalizada inmutable, CV = base + deltas efectivos de extras/cancelaciones/otras enmiendas; evidencia limpia, aceptación de revisión vigente y gerencia con contraseña reciente. Cancelación conserva trabajo reconocido mediante delta acotado y motivo; no borra costos ni crea caja. `asOf` reproduce corte sin recalcular historia. M2 implementa cobros/costos/pagos según [19](19-entrega-m2.md); BK-037 USD y conversiones. [Código y casos](17-entrega-m1.md).

## Implementación financiera M2

RN-F02/F03/F04/F05/F08/F09/F12/F15/F16/F17/F18 se implementan con la política inicial conservadora P-03/P-05 en CRC. Reportado/registrado no afectan caja/costo; validar reconoce devengo, confirmar registra caja y aplicar un anticipo no repite salida. NC/ND exigen original y cotejo de identidad, limitan base/impuestos y mantienen devolución separada. Costos fiscales hasta cinco decimales, movimientos hasta dos; un residual permanece explícito y no se redondea silenciosamente.

Correcciones de propuestas conservan revisiones e imputaciones. Reversiones y devoluciones conservan fuentes; deuda y saldo a favor se calculan por obligación/obra. Los cortes consultan hechos, estados y revisión efectivos, sin alterar cifras anteriores por la edición posterior de una propuesta. [Entrega y ejemplos ejecutados](19-entrega-m2.md).

CP/QA/CR, presupuestos, compromisos y USD permanecen en M5. El ejemplo A completo se verifica en el motor de fórmulas con entradas sintéticas; el reporte MVP no fabrica esas bases. Fondos tras obligaciones conocidas no incluyen costos futuros ausentes. P-05 no se interpreta como dictamen de deducibilidad ni utilidad fiscal.
