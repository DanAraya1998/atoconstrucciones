# 01 — Visión y alcance

[Índice](README.md) · [Historias](02-requerimientos-y-historias.md) · [Roadmap](13-roadmap-y-backlog.md)

## Objetivos confirmados

Centralizar el control de obras, contratos, cobros, costos, proveedores, cronogramas y evidencias de ATO Construcciones en Costa Rica. Ofrecer un sitio público y portales privados para clientes y proveedores; facilitar el trabajo de gerente y asistente desde móviles; conservar una API independiente consumible por futuras aplicaciones Flutter Android y Windows.

El éxito operativo se medirá por la capacidad de reconstruir el saldo de una obra, explicar una desviación de costos, identificar cobros pendientes y verificar quién aprobó cada cambio. Los objetivos comerciales del sitio se medirán con contactos efectivos y proyectos publicados con autorización, sin inventar metas ni cifras históricas.

## Inspección inicial del repositorio, anterior a M0

| Elemento existente | Resultado | Trabajo pendiente |
|---|---|---|
| `pom.xml` | Inspección inicial: Boot 4.1.1, `java.version=27`; actualización posterior autorizada: parent Boot **4.2.0**. Maven; starters MVC, JPA, Security, resource server OAuth2, validation, Flyway, mail, actuator y WebSocket | Resolver parent 4.2.0, validar compatibilidad final Java 27; configuración segura; contratos y módulos |
| `.mvn/wrapper/maven-wrapper.properties` | Wrapper 3.3.4, distribución Maven 3.9.16 | Verificar distribución, JDK y build en BK-001 |
| `src/main/java/.../AtoconstruccionesApplication.java` | Única clase productiva; arranca Spring | No hay controladores, servicios, repositorios ni entidades de negocio |
| `src/test/java/.../AtoconstruccionesApplicationTests.java` | Único test `contextLoads` | No acredita permisos, finanzas, XML ni funcionamiento empresarial |
| `src/main/resources/application.properties` | Solo nombre de aplicación | Perfiles, seguridad, DB y observabilidad pendientes |
| `compose.yaml` | PostgreSQL con etiqueta `latest` y configuración de ejemplo | Fijar versión y externalizar configuración en implementación; no reproducir sus valores en documentación |
| Frontend/OpenAPI/migraciones | No hay `package.json`, frontend, especificación API ni scripts SQL | Diseñar ahora; implementar en hitos posteriores |
| `.github/` | Hooks de modernización Java; no se encontró workflow de CI | No presumir CI/CD existente |
| `.agents/`, `.codex/` y ancestros | No se encontraron instrucciones AGENTS previas aplicables | Crear guía operativa solicitada |
| Git | Árbol limpio al iniciar; rama `master` con seguimiento remoto | No crear commit, push o cambios destructivos en esta entrega |
| PDF y logotipo | Disponibles en los adjuntos del usuario, fuera del repositorio | Análisis en [UX](07-diseno-ux-ui.md); manual/vector/fotos fuente no disponibles |

Esta tabla conserva la inspección histórica, no el estado actual. La implementación y pruebas de M0 están en [16](16-entrega-m0.md) y las de M1 en [17](17-entrega-m1.md).

## Usuarios y fronteras

- Visitante: conocer servicios y obras publicadas, contactar y entrar al login.
- CLIENTE: sus obras, estado aprobado, cobros, extras y documentos compartidos.
- PROVEEDOR: asignaciones, cotizaciones y consumos propios, sin balances del cliente o de otros proveedores.
- ASISTENTE_ADMINISTRATIVA: operación por obra y alertas de cobro; sin agregados financieros globales ni personales.
- GERENTE: gestión empresarial completa y finanzas personales separadas.
- ADMIN: soporte técnico; sin herencia funcional de GERENTE. Acceso excepcional según [RBAC](03-roles-y-permisos.md).

Se modela una única empresa, no SaaS multiempresa. Contactos/organizaciones podrán tener varios usuarios invitados; una obra tendrá un cliente contratante principal inicialmente, sujeto a P-04. No se infiere que un cliente o proveedor sea siempre una persona física.

## Alcance por entrega propuesta

**MVP (M0–M4):** plataforma segura, cartera de clientes/proveedores, proyectos/contratos, extras con evidencia de aprobación, depósitos reportados y confirmación humana, gastos y pagos separados, importación XML 4.4, comprobantes privados, indicadores por obra, portal cliente, Gantt básico con avance ponderado, sitio público con contacto y publicación autorizada, y operación recuperable. Incluye registro manual de caja y vinculación de comprobantes; no importación bancaria automática. La gerente puede consultar un resumen empresarial restringido desde M2; los reportes avanzados llegan después.

**Fase 2 (M5):** portal proveedor, comparación de cotizaciones/compromisos, finanzas personales, cierre/reapertura y PDF de reportes. El modelo del MVP reserva sus fronteras, pero no muestra menús vacíos ni promete estas capacidades en producción antes del hito.

**Avanzado (M6):** adaptadores bancarios con muestras, conciliación asistida, Calendar, planificación comercial adicional y clientes Flutter. Requieren evidencias/licencias/consentimiento que aún no existen. [Tareas concretas](13-roadmap-y-backlog.md).

## Exclusiones explícitas

La ejecución inicial no creó código de negocio, UI, entidades JPA, SQL, dependencias, integraciones ni ajustes de configuración. Posteriormente autorizó actualizar Boot y ejecutar M0; se implementaron fundamentos sintéticos con Java 27/Boot 4.2.0-M2, sin módulos financieros. El producto planificado no incluye contabilidad completa, nómina, compras/inventario completo, pagos bancarios iniciados por la plataforma, emisión/recepción tributaria automática ante Hacienda, firma digital certificada, aplicación Flutter implementada ni acceso automático al calendario del proveedor. Son ampliaciones que requerirían historias y decisiones propias.

## Calidad y supuestos de diseño

Objetivo propuesto: WCAG 2.2 AA, navegación táctil, flujos privados sin cache compartida, cálculos reproducibles al centavo, autorización en todas las capas y recuperación ensayada. Presupuestos de rendimiento y RPO/RTO son objetivos para validar con datos representativos, no garantías actuales; consultar [pruebas](12-pruebas-y-calidad.md) y [operación](14-despliegue-y-operacion.md).

Supuestos revisables: CRC en el MVP; fechas civiles de obra en Costa Rica; gerente como última autoridad financiera; tareas de duración en días calendario inicialmente; cuentas invitadas sin registro público abierto; gastos operativos mostrados en base bruta con impuestos desglosados. Cambiarlos requiere actualizar sus documentos y, cuando afecten estructura/contratos, un ADR.

## Registro central de decisiones pendientes

| ID | Decisión/evidencia y alternativas | Supuesto para avanzar | Se resuelve antes de |
|---|---|---|---|
| P-01 | Resuelta para M0: Java 27 + Boot 4.2.0-M2 aceptada tras fallar 4.2.0 final; build/arranque verificados | Conservar preview autorizada en desarrollo; actualizar y revalidar release estable | Liberación productiva |
| P-02 | Resuelta en desarrollo: Keycloak local, MFA desactivado. Proveedor productivo, SMTP y responsabilidad de recuperación pendientes | OIDC estándar, sesión JDBC web y bearer API, reautenticación reciente | Operación productiva / BK-029 |
| P-03 | ¿La asistente solo revisa o también confirma ingresos, valida gastos y registra pagos definitivos? | Predeterminado M2 implementado: prepara/revisa; GERENTE confirma, valida y revierte | BK-016/BK-018; matriz modificable mediante ADR |
| P-04 | Varios contratantes/representantes por obra y usuarios con varios roles | Confirmado 8/oct: un contratante, representantes y OPERATIONS explícitos; implementado M1 | BK-010 |
| P-05 | Impuestos incluidos en contratos, tratamiento de IVA recuperable, costos indirectos y criterio de resultado | Predeterminado M2 implementado: bruto/impuestos desglosados; sin deducibilidad ni utilidad fiscal certificada | BK-014/BK-019; revisión fiscal productiva pendiente |
| P-06 | Contratos USD, fuente/tipo de cambio y fechas de conversión | Confirmado 8/oct: M1 solo CRC; USD/conversión siguen BK-037 | BK-014; USD en BK-037 |
| P-07 | Quién acepta extras y qué evidencia contractual es suficiente: documento firmado, aceptación autenticada, firma certificada | GERENTE formaliza después de aceptación documentada; click no equivale a firma certificada | BK-015 |
| P-08 | Calendar: calendario de empresa/gerente, una o dos vías, destinatarios y scopes | Una vía ATO→calendario autorizado; invitaciones opcionales autorizadas | BK-041 |
| P-09 | Bancos/cuentas/formatos y muestras anonimizadas | Interfaces extensibles; carga no soportada produce diagnóstico, sin parsing inventado | BK-039 |
| P-10 | Hosting/región, conservación por clase documental, recuperación y titularidad de datos personales | Hosting local + Cloudflare Tunnel y RPO 24 h / RTO 4 h aceptados; responsable del aviso definida en sesión; correo/retención y ensayo productivo pendientes, sin borrado programado. [M4](21-entrega-m4.md) | BK-003/BK-013/BK-029 |
| P-11 | Google Drive elegido por el cliente; confirmar Mi unidad o unidad compartida, carpeta, custodios, consentimiento/credenciales, cuota y restore | La decisión posterior del usuario sustituye S3; adaptador Drive, antivirus y permisos ATO. Conexión real pendiente | BK-011 / operación productiva |
| P-12 | M3 usa días calendario y pesos físicos explícitos; feriados/esfuerzo/otras metodologías requieren política posterior | Predeterminado implementado para desarrollo en [20](20-entrega-m3.md), sin certificación del negocio | BK-022/BK-023 |
| P-13 | Manual/vector de marca, textos, datos de contacto, cifras, derechos de fotografías | Colores muestreados, contenido pendiente y galería desactivada sin autorización | BK-025/BK-026 |

El intento inicial de resolver Boot 4.2.0 final falló antes de compilar. El usuario aceptó 4.2.0-M2 para M0; la validación posterior sí ejecutó compilación, pruebas y arranque con Java 27 y PostgreSQL 18.6. No acredita soporte de una release final futura. [ADR 0002](adr/0002-stack-y-java-27.md), [evidencia M0](16-entrega-m0.md).
