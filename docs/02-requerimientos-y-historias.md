# 02 — Requerimientos e historias

[Índice](README.md) · [Permisos](03-roles-y-permisos.md) · [Finanzas](08-reglas-financieras.md) · [Trazabilidad](15-trazabilidad.md)

## Convenciones

HU-01 a HU-10 conservan la identidad del pedido. HU-00, HU-11 y HU-12 descomponen requisitos transversales explícitos; no agregan otro producto. P0 = imprescindible para operación segura, P1 = siguiente incremento funcional, P2 = avanzado. Cada criterio CA es verificable y debe convertirse en prueba identificada en [calidad](12-pruebas-y-calidad.md). Reglas de acceso RN-Axx, finanzas RN-Fxx, cronograma RN-Gxx, documentos RN-Dxx y cierre RN-Cxx viven en sus capítulos canónicos.

## HU-00 — Sitio público y contacto

**Actores:** visitante, GERENTE. **Prioridad:** P0/MVP. **Dependencias:** HU-11 para administración, HU-12 para publicación. **Reglas:** RN-A01, RN-D06.

Como visitante quiero conocer servicios y proyectos autorizados y contactar a ATO para evaluar una obra.

- **CA-00.1:** header presenta logo a la izquierda e Inicio, ATO, Servicios, Galería (ruta `/proyectos`), Contacto y Login; desde móvil hay menú accesible y CTA de contacto. Etiquetas actualizadas por el rediseño autorizado del 27/10/2026, sin retirar rutas previas.
- **CA-00.2:** solo se publican fichas y variantes fotográficas autorizadas; la respuesta pública omite contrato, cliente, dirección exacta, saldos y originales privados.
- **CA-00.3:** servicios, empresa, ficha de proyecto y contacto funcionan con teclado, a 360 px y movimiento reducido; contenido comercial tiene aprobación previa.
- **CA-00.4:** el formulario valida nombre de contacto, medio de respuesta y mensaje mínimos, registra aviso de privacidad y limita spam; no revela si un correo ya tiene cuenta.
- **CA-00.5:** URLs públicas tienen metadata/alt/sitemap, imágenes con tamaños y carga diferida; páginas privadas no se indexan ni aparecen en sitemap.
- **CA-00.6 (27/10/2026):** rediseño editorial de Inicio/presentación pública con lorem ipsum autorizado y conceptos arquitectónicos identificados como muestra. Sin contenido aprobado no se vacían las secciones; `noindex` continúa y los borradores no se muestran. Textos/fotos aprobados conservan prioridad. [Entrega y pruebas](27-rediseno-publico-y-lorem-ipsum.md).

**Excepciones:** material sin consentimiento queda en borrador; cifras del PDF no verificadas no se publican; falla de correo conserva contacto y muestra confirmación de recepción, no de entrega. Los proyectos terminados anteriores se pueden cargar como fichas de portafolio independientes sin inventar su historial financiero.

## HU-01 — Proyectos y contratos

**Actores:** GERENTE; asistente consulta y prepara según matriz. **Prioridad:** P0/MVP. **Dependencias:** HU-11, cartera de clientes. **Reglas:** RN-A02, RN-F01, RN-C01.

Como gerente quiero registrar una obra con su cliente y contrato para controlar su ejecución.

- **CA-01.1:** creación asigna UUID interno y código visible único, cliente existente, ubicación descriptiva y coordenadas opcionales, número de contrato, contacto empresarial, fechas y monto/moneda. Código/UUID no contienen datos personales.
- **CA-01.2:** fechas validan inicio ≤ final estimada; latitud/longitud dentro de rango; monto no negativo; contacto normalizado y validado sin suponer que es identificador del cliente.
- **CA-01.3:** estado sigue BORRADOR→ACTIVO↔SUSPENDIDO→EN_CIERRE→CERRADO; CANCELADO requiere motivo y resolución de compromisos. Solo GERENTE activa, suspende, cancela o cierra.
- **CA-01.4:** activar requiere contrato base y miembros autorizados. El número de contrato se identifica por empresa y revisión, sin reutilizarlo para dos contratos vigentes; los cambios monetarios se registran como enmiendas versionadas.
- **CA-01.5:** dos cambios simultáneos no se pisan: versión obsoleta se rechaza y el usuario puede revisar el nuevo estado.

**Excepciones:** cliente desactivado no recibe nuevas obras sin revisión; obra sin ubicación exacta puede existir en borrador; no borrar una obra con movimientos. Las relaciones de representantes adicionales quedan en P-04.

## HU-02 — Depósitos reportados e ingresos confirmados

**Actores:** GERENTE, ASISTENTE_ADMINISTRATIVA, CLIENTE. **Prioridad:** P0/MVP. **Dependencias:** HU-01, HU-11, HU-12. **Reglas:** RN-F02, RN-F03, RN-F09, RN-A02.

Como usuario autorizado quiero reportar un depósito para que se verifique e impute al saldo de mi obra.

- **CA-02.1:** reporte contiene proyecto autorizado, monto positivo, moneda, fecha de operación, medio de pago, referencia si aplica, evidencia, actor y fecha de registro. El cliente no puede indicar otro registrador ni estado confirmado.
- **CA-02.2:** REPORTADO/EN_REVISION no cambia ingresos confirmados ni saldo de cobro. GERENTE confirma con evidencia bancaria o de efectivo y una imputación a la obra; asistente prepara revisión bajo P-03.
- **CA-02.3:** confirmación crea o vincula un único movimiento de tesorería e imputación; repetir la misma petición idempotente devuelve el mismo resultado, sin duplicar ingresos.
- **CA-02.4:** referencia coincidente genera alerta, no rechazo automático por sí sola; duplicado exacto de imputación/movimiento se bloquea. Rechazo exige motivo visible apropiado para el reportante.
- **CA-02.5:** saldo usa monto vigente y cobros aplicados netos; sobrepago se presenta como crédito del cliente. Reversión requiere motivo, permiso y vínculo al ingreso original.

**Excepciones:** comprobante en cuarentena no se descarga; transferencia en USD a contrato CRC exige conversión registrada; fecha futura se rechaza o se convierte en borrador planificado sin confirmar; depósitos parciales y varios reportes de un mismo movimiento se resuelven sin doble imputación.

## HU-03 — Gastos, comprobantes y pagos

**Actores:** GERENTE, asistente; proveedor reporta consumos propios en fase 2. **Prioridad:** P0/MVP. **Dependencias:** HU-01, HU-02 para tesorería común, HU-12. **Reglas:** RN-F04, RN-F05, RN-F08, RN-D01 a RN-D05.

Como operadora quiero registrar costos de una obra y adjuntar documentos para validar su imputación y distinguir deuda de pago.

- **CA-03.1:** gasto lleva obra, fecha de devengo/servicio, categoría, proveedor opcional justificado, descripción, base/impuestos/total/moneda, origen y estado. Registrarlo no genera un pago.
- **CA-03.2:** XML 4.4 pasa validación local segura, tipo/namespace y XSD oficial versionado; conserva clave, emisor, receptor, moneda, totales, líneas y documento original. Un XML estructuralmente inválido no se valida como factura.
- **CA-03.3:** XML, PDF e imagen pueden referir al mismo comprobante; duplicado de clave fiscal no crea otro costo. Se muestra diferencia entre total fiscal y porción imputada a esta obra.
- **CA-03.4:** GERENTE valida imputaciones y diferencias con motivo; asistente registra/revisa. Gasto validado afecta costo devengado; pago confirmado afecta caja. Anticipo no genera costo devengado hasta el reconocimiento posterior.
- **CA-03.5:** nota de crédito/reversión se vincula al original, reduce el costo correspondiente una sola vez y conserva pagos/devoluciones como flujos independientes.
- **CA-03.6:** asistente ve operación por obra; los endpoints/exportaciones no permiten construir un dashboard financiero global reservado.

**Excepciones:** factura para receptor distinto de ATO se marca para revisión; emisión antigua no se descarta por regla inventada; tipo tributario no soportado se registra como documento pendiente, sin validar como gasto fiscal; un gasto sin factura requiere justificación y política pendiente, sin inferir deducibilidad tributaria.

## HU-04 — Proveedores, cotizaciones y compromisos

**Actores:** GERENTE y PROVEEDOR; asistente registra según matriz. **Prioridad:** cartera P0/MVP; portal y comparación P1/fase 2. **Dependencias:** HU-01, HU-05 cuando cotiza un extra, HU-11. **Reglas:** RN-F06, RN-F07, RN-A03.

Como gerente quiero comparar ofertas y su consumo para controlar presupuestos y sobrecostos.

- **CA-04.1:** proveedor puede ser persona/organización con contactos, categorías múltiples, teléfono, correo, zona e identificación comercial cuando necesaria. Desactivación conserva histórico.
- **CA-04.2:** cotización tiene versiones, moneda, impuestos, líneas, condiciones, vigencia, origen PROVEEDOR/MANUAL, proyecto y extra opcional; carga manual registra su procedencia sin inventar acceso del proveedor.
- **CA-04.3:** BORRADOR→RECIBIDA→EN_REVISION→APROBADA/RECHAZADA; SUSTITUIDA conserva versión original. Aprobación interna es GERENTE y crea un compromiso identificable, no un gasto/pago ni aprobación del extra del cliente.
- **CA-04.4:** pantalla compara aprobado, costo reportado, validado, pagado, compromiso restante y desviación, bajo la misma moneda/base fiscal. Anticipos se muestran aparte.
- **CA-04.5:** cada proveedor solo consulta su oferta/asignación y registra consumos propios; no ve ofertas de competidores, margen ATO o saldo del cliente.
- **CA-04.6 (27/10/2026):** registrar un proveedor en cartera no exige obra ni cuenta de usuario y no concede accesos. Las asignaciones se realizan posteriormente con permisos explícitos.
- **CA-04.7 (27/10/2026):** catálogo inicial con categoría principal controlada, detalle libre conservado y filtro paginado autorizado: maestro de obras, electricista, plomero, vidriero, maquinaria, acabados, ferretería y otros. Las categorías múltiples de CA-04.1 permanecen pendientes del alcance completo. [Implementación y límites](26-reprogramacion-y-proveedores.md).

**Excepciones:** oferta vencida exige renovación o aprobación motivada; sustituir compromiso consumido requiere enmienda y conciliación del remanente; exceso alerta y requiere revisión, no aumenta automáticamente el contrato del cliente.

## HU-05 — Extras contractuales

**Actores:** GERENTE, CLIENTE; proveedor cotiza si asignado. **Prioridad:** P0/MVP. **Dependencias:** HU-01, HU-12; HU-04 opcional. **Reglas:** RN-F01, RN-F10, RN-A02.

Como cliente o gerente quiero solicitar trabajo adicional, evaluar precio/plazo y formalizar su autorización.

- **CA-05.1:** solicitud guarda alcance y origen; puede tener ofertas de proveedor y precio al cliente separados. SOLICITADO/COTIZANDO/PENDIENTE_APROBACION no aumenta monto contractual.
- **CA-05.2:** versión aprobada contiene alcance, importe/moneda/impuestos, impacto de plazo, evidencia de aceptación y formalizador. Cambiar cualquiera exige nueva versión.
- **CA-05.3:** bajo propuesta P-07, cliente autorizado registra aceptación o evidencia, y GERENTE formaliza; aprobación contractual efectiva genera una sola enmienda y enlaza ejecución/tareas.
- **CA-05.4:** enlaza proveedores, ofertas, gastos, imputaciones de cobro/pago, cronograma y documentos del mismo proyecto; IDs de otro proyecto se rechazan.
- **CA-05.5:** APROBADO→EN_EJECUCION→TERMINADO. Cancelación posterior a aprobación exige enmienda negativa y liquidación del trabajo ya reconocido.

**Excepciones:** sin acuerdo válido queda pendiente; rechazo conserva cotizaciones; cliente no puede fijar unilateralmente precio aprobado; aceptación en portal no se declara firma certificada.

## HU-06 — Conciliación bancaria

**Actor:** GERENTE. **Prioridad:** P2/avanzado. **Dependencias:** HU-02, HU-03, HU-07, HU-12; P-09. **Reglas:** RN-F11, RN-F12, RN-A05.

Como gerente quiero contrastar movimientos bancarios con la tesorería registrada para detectar errores y pendientes.

- **CA-06.1:** importación identifica banco/cuenta, formato, hash, versión de adaptador y errores por fila. Sin adaptador probado, devuelve NO_SOPORTADO y no inventa movimientos.
- **CA-06.2:** normaliza fecha de operación/valor, monto firmado, moneda, referencia y fuente; detecta mismo lote/identificador bancario y trata coincidencias débiles como revisión.
- **CA-06.3:** propuestas explican coincidencia por importe/moneda/referencia/fecha; ninguna queda definitiva sin aprobación humana.
- **CA-06.4:** permite uno-a-varios y varios-a-uno con importe residual; confirmar vincula al movimiento existente o crea uno autorizado, sin duplicar el depósito/pago previamente confirmado.
- **CA-06.5:** clasificación de no asociado distingue empresa, personal, transferencia interna, comisión y desconocido; efectivo usa registro manual sin fingir línea bancaria.
- **CA-06.6:** corrección deshace vínculo y, si corresponde, revierte imputación; no elimina línea fuente ni decisiones previas.

**Excepciones:** PDF sin texto/mala extracción se mantiene pendiente; líneas ambiguas no se concilian automáticamente; no se importan cuentas personales dentro del ámbito empresarial por conveniencia.

## HU-07 — Información empresarial global y finanzas personales

**Actor:** GERENTE exclusivamente en uso funcional. **Prioridad:** resumen empresarial P0/MVP; personal/reportes P1/fase 2. **Dependencias:** HU-02, HU-03, HU-11, HU-12. **Reglas:** RN-F13, RN-A04, RN-A05.

Como gerente quiero revisar métricas empresariales globales y administrar ingresos/gastos personales en un ámbito independiente.

- **CA-07.1:** reportes empresariales especifican período, moneda, base de costo y métricas de contrato/devengo/caja; no usan un campo ambiguo “balance”.
- **CA-07.2:** transacciones/cuentas/categorías personales viven en tablas/esquema y servicios separados, sin `projectId` ni inclusión en consultas empresariales.
- **CA-07.3:** asistente, proveedor, cliente y ADMIN reciben denegación en endpoints, exports, búsqueda y documentos personales. La asistente solo recibe cola de cobros por obra autorizada.
- **CA-07.4:** PDF incluye filtros, corte temporal y definición de indicadores; generación/descarga se vuelve a autorizar y se audita.
- **CA-07.5:** mover fondos entre empresa y persona exige dos operaciones autorizadas enlazadas, clasificación explícita y exclusión de transferencias de ingresos/costos productivos.

**Excepciones:** importación personal mal clasificada va a revisión privada; tasa faltante impide total consolidado; soporte excepcional no convierte ADMIN en usuario funcional del módulo.

## HU-08 — Cronograma y avance físico

**Actores:** GERENTE; asistente edición operativa autorizada; PROVEEDOR reporta lo asignado; CLIENTE ve resumen. **Prioridad:** P0 básico/MVP; avanzado P2. **Dependencias:** HU-01, HU-04 para asignación, HU-11. **Reglas:** RN-G01 a RN-G08.

Como gerente quiero planificar tareas y verificar avance físico para informar fechas y retrasos confiables.

- **CA-08.1:** tareas hoja, etapas e hitos guardan intervalos válidos, progreso, versión y asignaciones; las etapas agregan hojas y no duplican su peso.
- **CA-08.2:** backend rechaza ciclo de dependencias/jerarquía, vínculos entre proyectos, intervalos y desfases incompatibles; un drag inválido no se persiste.
- **CA-08.3:** concurrencia protege la versión del cronograma completo; dos modificaciones incompatibles no sobreviven juntas por usar versiones de tareas separadas.
- **CA-08.4:** avance del cliente usa pesos de una revisión aprobada y progresos verificados; sin pesos/cobertura válidos muestra “avance no calculable” y corte/alcance pendientes.
- **CA-08.5:** proveedor ve tareas propias y dependencias externas solo como restricciones resumidas; cliente no recibe el Gantt administrativo, costos, nombres privados ni tareas internas.
- **CA-08.6:** móvil presenta agenda/lista por etapa con edición por formulario; el Gantt amplio se abre como vista opcional. No se requiere arrastrar para completar un flujo.
- **CA-08.7 (27/10/2026):** reprogramar inicio conservando duración, ajustar último día/duración o desplazar días; hitos de fecha única. Formulario/arrastre/resize preparan un lote con motivo, revisión e historial. No se mueven sucesoras sin una edición explícita; conflictos conservan el borrador.
- **CA-08.8 (27/10/2026):** Gantt con altura según filas y vista ampliada; varias tareas continúan visibles, los planes largos conservan scroll y Escape devuelve el foco al editor. [Implementación y pruebas](26-reprogramacion-y-proveedores.md).

**Excepciones:** replanificación/extra puede reducir porcentaje por cambio del alcance, con explicación; hitos no avanzan por pasar la fecha; funciones PRO no se prometen en Community.

## HU-09 — Google Calendar

**Actor:** GERENTE; proveedores reciben invitación solo cuando se autorice. **Prioridad:** P2/avanzado. **Dependencias:** HU-08, HU-11, HU-12; P-08. **Reglas:** RN-A06, RN-D07.

Como gerente quiero publicar actividades autorizadas en mi calendario conectado para coordinar trabajos.

- **CA-09.1:** conexión requiere OAuth, calendario elegido y consentimiento; desconectar elimina acceso futuro y programa revocación de tokens.
- **CA-09.2:** cada tarea/evento tiene mapping único; reintentar después de timeout verifica ID existente y no crea otra invitación.
- **CA-09.3:** cambio/cancelación se encola después del commit; error de Google no revierte una fecha válida en ATO. Se muestran pendiente/error y reintento controlado.
- **CA-09.4:** invitación permite aceptar/rechazar, pero no otorga permiso de escribir en calendario personal del proveedor. Ese acceso requiere consentimiento separado y nueva decisión.
- **CA-09.5:** propuesta inicial una vía: cambios remotos se muestran como discrepancia y no sobrescriben fechas en ATO; futura bidireccionalidad requiere reglas y pruebas nuevas.

**Excepciones:** token revocado necesita reconexión; cuotas/429 usan backoff; payload de evento no incluye finanzas o ubicación privada sin necesidad y autorización.

## HU-10 — Cierre, reapertura y publicación

**Actor:** GERENTE. **Prioridad:** cierre P1/fase 2; publicación autorizada P0/MVP. **Dependencias:** HU-01 a HU-05, HU-08, HU-12. **Reglas:** RN-C01 a RN-C04, RN-D06.

Como gerente quiero cerrar una obra con una revisión reproducible y decidir qué evidencia puede publicarse.

- **CA-10.1:** EN_CIERRE genera checklist de cobros, pagos/gastos por validar, extras, compromisos, documentos y cronograma; cada bloqueo se resuelve o se exceptúa con motivo/autorización.
- **CA-10.2:** cierre guarda snapshot por moneda con versión de fórmulas, fecha de corte, contrato/enmiendas, costos, caja, saldos y pendientes aceptados. No convierte pendiente en cero.
- **CA-10.3:** CERRADO impide mutaciones operativas ordinarias; GERENTE puede reabrir con razón, auditoría y nueva revisión. El snapshot anterior permanece intacto.
- **CA-10.4:** publicación es proceso independiente: evidencia de consentimiento, selección de imágenes, eliminación de metadatos y aprobación editorial. Cerrar no publica automáticamente.
- **CA-10.5:** retirada de autorización impide servir nuevamente la variante pública, purga caches controlables y conserva evidencia privada de la decisión según retención.

**Excepciones:** cierre con saldo requiere política pendiente y exposición explícita; corrección tardía produce nueva versión; un contacto o rostro visible requiere alcance de consentimiento adecuado.

## HU-11 — Identidad, membresías y soporte seguro

**Actores:** todos los roles. **Prioridad:** P0/prerrequisito. **Dependencias:** P-02 y decisión de soporte. **Reglas:** RN-A01 a RN-A08.

Como responsable quiero que cada usuario opere únicamente sobre recursos autorizados y que soporte tenga límites comprobables.

- **CA-11.1:** autenticación verifica identidad; invitaciones/recuperación caducan y son de un uso; MFA desactivado por decisión del usuario; acciones sensibles exigen reautenticación reciente con contraseña. Recuperación por correo pendiente de SMTP productivo.
- **CA-11.2:** autorización se ejecuta en backend para acciones, pertenencia y campo confidencial; UUID conocido, filtro/URL manipulado o `projectId` enviado por cliente no otorgan acceso.
- **CA-11.3:** revocar miembro/sesión impide nuevos accesos; no hay impersonación libre ni privilegios por rol enviado en la petición.
- **CA-11.4:** ADMIN utiliza diagnósticos sin contenido financiero; acceso excepcional requiere grant otorgado por GERENTE, propósito, alcance, caducidad y auditoría.
- **CA-11.5:** un usuario con varios roles no combina PROVEEDOR/CLIENTE/ASISTENTE para obtener finanzas personales o globales; la capacidad GERENTE debe otorgarse explícitamente.
- **CA-11.6 (27/10/2026):** alta directa exclusiva de GERENTE crea identidad y aprueba perfil/cartera/obras en una acción; la persona verifica correo y configura contraseña en el IdP. Registro propio queda pendiente y sin permisos hasta aprobación con accesos. Sin fusión por correo ni concesión ADMIN/GERENTE. Decisiones auditadas/idempotentes/concurrentes, suspensión reversible y activación reintentable; [ADR 0017](adr/0017-altas-y-aprobacion-de-cuentas.md).
- **CA-11.7 (27/10/2026):** `/login` entra directamente al IdP con identidad visual ATO; acceso, registro, verificación, activación y recuperación conservan formularios/acciones nativas, español/inglés y operación móvil/teclado. Sin cambiar emisor, permisos ni aprobación gerencial. [Entrega y configuración](28-login-con-identidad-ato.md).

**Excepciones:** último gerente activo no se desactiva sin sucesión; caída del IdP no abre acceso; fallo de auditoría impide mutaciones críticas.

## HU-12 — Evidencias privadas y trazabilidad

**Actores:** usuarios autorizados por recurso. **Prioridad:** P0/prerrequisito. **Dependencias:** HU-11, P-10/P-11. **Reglas:** RN-D01 a RN-D07, RN-A07.

Como usuaria quiero adjuntar/consultar evidencias con protección y conocer el historial de decisiones.

- **CA-12.1:** upload de objeto limitado por tipo/tamaño queda en cuarentena; hash, tamaño y tipo real se verifican antes de habilitarlo. Compartir no evita el escaneo.
- **CA-12.2:** documento se enlaza explícitamente a su recurso y audiencia; privado por defecto; toda descarga revalida autorización y usa URL breve o streaming según sensibilidad.
- **CA-12.3:** auditoría conserva actor real, instante, recurso, acción, motivo, versiones y `traceId`, sin almacenar tokens ni contenido documental completo.
- **CA-12.4:** reversión no borra evidencia ni historia; un borrado permitido por retención es auditable y no rompe referencias de registros financieros retenidos.
- **CA-12.5:** backup y restauración recuperan DB, objetos, claves y vínculos probados; no se presenta como recuperable solo porque exista un dump.

**Excepciones:** archivo infectado se bloquea; scanner no disponible mantiene cuarentena; objeto huérfano se limpia tras política de retención de temporales; usuario pierde acceso aunque conserve metadata del documento.
