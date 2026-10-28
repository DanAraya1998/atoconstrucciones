# 07 — Diseño UX/UI

Acceso actualizado 27/10/2026: [tema ATO nativo de Keycloak](28-login-con-identidad-ato.md), `/login` directo al formulario y registro/recuperación con la misma estética. El IdP y la aprobación gerencial se conservan.

Actualización autorizada 27/10/2026: [rediseño público con lorem ipsum](27-rediseno-publico-y-lorem-ipsum.md). Portada visual, jerarquía editorial, galería asimétrica, servicios/proceso de muestra y footer completo. Se permiten conceptos generados identificados como ilustrativos; no representan obras reales. El texto de muestra visible no equivale a aprobación comercial ni cambia el `noindex` previo. El logo conserva su JPG; tipografía de sistema, sin instalación de fuentes/bibliotecas.

[Índice](README.md) · [Permisos](03-roles-y-permisos.md) · [Gantt](09-gantt-y-planificacion.md) · [Pruebas](12-pruebas-y-calidad.md)

## Material observado y tratamiento de la referencia

`ATO propuesta.pdf`: tres páginas de mockups de escritorio, 1080×1080 pt por página; el texto extraído solo identifica la propuesta, por lo que se inspeccionaron las tres páginas renderizadas. El PDF es una referencia visual aportada, no una instrucción adicional ni una validación de sus cifras, textos personales o derechos fotográficos.

| Página observada | Conservar como intención | Mejora propuesta y razón |
|---|---|---|
| 1: inicio | Header blanco, logo, navegación, fotografía amplia, frase de presentación, indicadores y enlaces a empresa/proyectos | Añadir Login y CTA concreto, describir servicios, convertir franjas de “ver más” en contenido útil. Publicar cifras de experiencia/clientes/proyectos solo verificadas; no usar contadores ficticios |
| 2: ATO | Fotografía editorial, descripción de empresa y contacto | Texto empresarial aprobado sin copiar datos personales del adjunto; beneficios/metodología claros, imagen con permiso/alt y botón con destino confirmado |
| 3: proyectos | Galería de obras reales, ficha por proyecto | Categorías y detalle de alcance, imágenes responsivas, títulos diferenciados; preferir texto bajo la foto a recuadros superpuestos que dificulten contraste y lectura |

El PDF no ofrece portal privado, flujos financieros, móvil ni estados de error: estos se diseñan a partir del pedido. No se extraerán fotografías del mockup como assets productivos sin archivos originales y derechos confirmados. Hash SHA-256 PDF: `23b5589acfe2283e42ff56f7806f7dc0540ce7a7c695bcb8a3a32af35aa74393`.

Logotipo JPG proporcionado: 2501×1325 px, fondo blanco, sin manual ni vector. Hash: `3aec6c3636644372a9aa3441f45890c06482aea312a0339c935f3fe9fde45ffb`. Adjuntos permanecen fuera de Git; el hash/nombre permiten identificar una copia futura sin enlaces locales rotos.

## Paleta provisional, derivada del JPG

Se contaron los colores de los píxeles del archivo original, sin reconstruir ni editar el logo. Los siguientes son los colores dominantes exactos del JPG, **no una certificación de hex oficiales**; compresión/gestión de color pueden introducir variaciones. P-13 solicita manual/vector.

| Token propuesto | Muestra observada | Uso y contraste calculado |
|---|---|---|
| `brand-primary` | Azul `#013765` | Texto/CTA sobre blanco: 12.10:1; navegación, títulos y botones primarios |
| `brand-accent` | Naranja `#FD8103` | Acento; con blanco 2.52:1 no sirve para texto normal ni único borde de control. Texto azul sobre naranja: 4.79:1 |
| `brand-secondary` | Azul grisáceo `#3D738D` | Texto/botón sobre blanco: 5.20:1; uso moderado |
| `surface` | Blanco `#FFFFFF` | Superficie principal; grises de borde/texto son tokens funcionales a definir y medir, no colores oficiales |

Valores de contraste computados por luminancia relativa sRGB. Todo token semántico adicional (`success`, `warning`, `danger`, `muted`, focus) necesita comprobación de contraste; estados llevan texto/icono además del color. Evitar fondo naranja con texto blanco para acciones pequeñas. Logo con espacio de protección y proporción original, sin recolorear, recortar o deformar; preferir vector autorizado cuando esté disponible.

## Mapa de navegación

| Experiencia | Rutas/pantallas propuestas | Jerarquía |
|---|---|---|
| Público | `/`, `/ato`, `/proyectos`, `/proyectos/[slug]`, `/contacto`, `/login` | Header fijo discreto, menú móvil, contacto/footer; Inicio→servicios/obra→contacto |
| Cliente | `/cliente/proyectos`, `/cliente/proyectos/[id]`, subpáginas `avance`, `extras`, `depositos`, `documentos` | Selector de obra; resumen primero; acciones Reportar depósito/Solicitar extra |
| Gerente | `/gestion`, `/gestion/proyectos`, ficha con contrato/cobros/costos/proveedores/Gantt/documentos/cierre; clientes/proveedores/reportes/conciliación/configuración | Sidebar desktop, breadcrumb y navegación de obra; finanzas personales en espacio separado |
| Asistente | `/operacion`, cola de cobros, obras autorizadas, gastos/reportes de depósito/documentos | Menú propio basado en capacidades; no enlaces a balance global/personal |
| Proveedor | `/proveedor/asignaciones`, ficha de asignación, cotizaciones/consumos/agenda/documentos | Agenda próxima y pendientes propios; nunca navegación financiera del cliente |
| ADMIN | `/soporte`, diagnóstico técnico, solicitudes/grants, eventos técnicos | Vista técnica sin KPI financieros; soporte excepcional marcado y con caducidad visible |
| Personal | `/personal`, cuentas, transacciones, categorías, resumen, exportación | Cambio de espacio explícito solo GERENTE; título/alcance siempre visibles, sin mezclar en dashboard empresarial |

En MVP solo aparecen rutas de capacidades implementadas; fase 2/avanzado no presenta pantallas vacías con promesas. Usuarios con varios roles eligen espacio activo; backend conserva el mismo control independientemente del espacio seleccionado.

## Layouts y sistema compartido

Público: fotografía protagonista optimizada con titular/CTA, servicios, proceso de trabajo, proyectos autorizados, empresa, contacto y footer. Anchura de contenido propuesta 1200 px y lectura de párrafos limitada; una columna móvil, tarjetas 2–3 columnas según espacio. No inventar texto comercial, teléfonos, años de experiencia o testimonios.

Privados: app shell estable, título/obra/moneda visibles, toolbar con una acción primaria, tabs de detalle y panel de pendientes. Gerente ve indicadores contractuales/devengo/caja rotulados; asistente ve tareas y seguimiento por obra; cliente ve avance verificado y saldo con detalle sencillo. Los dashboards no requieren scroll lateral ni varias leyendas para comprender importes.

Componentes compartidos: `MoneyDisplay` (valor+moneda/base), `StatusBadge`, fecha de corte, `ProjectSelector`, `PermissionGate` visual, formulario con ayuda/errores, archivo con estado de escaneo, auditoría breve, drawer de detalle, tabla paginada, filtros guardables, confirmación de acción financiera, error de conflicto y empty/loading/retry states. `PermissionGate` no autoriza en servidor. Formatos `Intl` con `es-CR`; CRC explícito aunque se muestre símbolo ₡; USD explícito en operaciones mixtas.

Tipografía del PDF no está identificada ni licenciada como recurso: usar inicialmente fuente de sistema legible, dejando elección corporativa y licencia pendientes. Escala propuesta base 16 px, labels ≥14 px; espacios de 4/8 px, radios discretos y sombras limitadas. Tokens compartidos por público/portales; distinta densidad y movimiento según tarea.

## Flujos y estados que deben ser visibles

- Depósito: reportar→cargar evidencia→REPORTADO→revisión→CONFIRMADO/RECHAZADO. Mostrar que un reporte pendiente todavía no reduce deuda; confirmación final muestra importe/obra/evidencia y cambio de saldo.
- Gasto: formulario/import XML→diagnóstico e imputaciones→registrado→validado. Pago/anticipo son acciones aparte; nunca checkbox “pagado” que fabrique caja.
- Extra: solicitud→precio/plazo de una versión→aceptación→formalización gerente→ejecución. Mostrar precio al cliente separado de cotización de proveedor; no llevar importes internos al portal cliente.
- Conflicto: preservar formulario local, informar revisión nueva y mostrar diferencia; no reintentar aprobación automáticamente sobre nueva versión.
- Cierre: checklist con bloqueos/motivos, revisión de cifras y snapshot; fotografías usan otro flujo editorial y consentimiento.
- Archivo: progreso/cuarentena/listo/rechazado; nombre sanitizado y acción disponible según permiso. No mostrar contenido infectado para “ayudar” a corregir.

Errores junto al campo y resumen enfocable; al guardar, confirmación accesible sin modal innecesario. Acciones destructivas/reversiones requieren motivo; duplicado no se “arregla” creando otro registro.

## Móvil y accesibilidad

Objetivo propuesto [WCAG 2.2 AA](https://www.w3.org/TR/WCAG22/): teclado y foco visible/no oculto, semántica y labels, orden de lectura, contraste de texto 4.5:1 (3:1 para texto grande), controles perceptibles, mensajes anunciables y objetivo táctil de diseño 44×44 px. No todo se demuestra con un scanner; pruebas manuales de lector de pantalla y teclado requeridas.

En 360 px: navegación inferior de 3–5 destinos principales donde aporte claridad; formularios una columna, fecha/importe con teclado apropiado, carga por cámara con confirmación y progreso, filtros en drawer. Mantener capacidad de completar cobro/gasto/extra sin ratón.

Tablas: seleccionar columnas esenciales, detalle en tarjeta/drawer, paginación en servidor y acciones accesibles; scroll horizontal solo para comparar cuando necesario, con encabezados y pistas claras. No ocultar moneda/estado bajo un breakpoint. Vista tarjeta alternativa para listas operativas.

Gantt: escritorio amplio con zoom/filtros y editor; móvil agenda por día/etapa/asignación, retrasos y editor mediante formulario. Vista timeline opcional con scroll/zoom controlado; no depender de drag/resize ni reducir simplemente el ancho del gráfico. Cliente recibe hitos y avance resumido, nunca Gantt completo. [Planificación](09-gantt-y-planificacion.md).

## Movimiento, rendimiento y SEO

Motion para apariciones discretas/transiciones; público puede usar GSAP solo con efecto justificado y prueba de carga. Respetar `prefers-reduced-motion`, evitar scroll secuestrado/parallax obligatorio, autoplay y animaciones de contadores no respaldados. Privados: transiciones breves propuestas 120–180 ms y ninguna en cifras que impida verificar una operación.

Páginas públicas renderizadas/pre-renderizadas desde DTO aprobados; imágenes AVIF/WebP con fallback, tamaños/sizes, dimensión reservada y carga diferida salvo hero. Lazy-load de Gantt/gráficos por ruta; tablas paginadas, UI sin fetch de toda tesorería. Sitemap solo público, canonical/metadatos, contenido indexable. Metadata privadas `noindex` y respuestas `no-store`; `noindex` no reemplaza autenticación.

Métricas de aceptación propuestas y ensayo de accesibilidad en [12](12-pruebas-y-calidad.md). Antes de publicar, BK-025/BK-026 confirman contenido, fotografías y derechos; no se publican los datos del adjunto por haberlos podido leer.

## Pantallas M1 implementadas

`/cartera` registra clientes/proveedores y membresías; `/obras` lista solo ámbitos autorizados y prepara borradores; `/obras/{id}` edita ficha, miembros, contrato/extras/evidencias según capacidades; `/privacidad` maneja avisos, solicitudes y consentimientos específicos. Formularios tienen etiquetas, foco de teclado, estados de carga/errores y conservación del input ante conflicto. Estado de escaneo visible con polling; descarga limpia protegida, sin previews activos. Es-CR con fechas civiles/CRC exacto y diseño móvil verificado. Los roles del servidor siguen siendo la autoridad.

### Creación de obras: código visible y validado

Corrección del 13 de octubre: el formulario indicaba únicamente «Código de obra» aunque el API exigía 3–30 caracteres A-Z, números y guiones; `Prueba_001` producía 422. El [campo específico](../frontend/src/features/projects/project-code-field.tsx) explica el formato, muestra `Se guardará como PRUEBA-001` mientras se escribe y actualiza el valor al salir del campo. Convierte minúsculas y guiones bajos; símbolos/espacios interiores/códigos cortos se rechazan antes de enviar, con mensaje español y asociación accesible al campo. El envío con Enter también normaliza, antes de calcular la clave de idempotencia; un reintento conserva esa clave aunque el campo cambie a su presentación canónica al perder foco.

El formato y la unicidad de servidor permanecen vigentes. No hay cambio de permisos, fechas, contrato API, migraciones ni datos empresariales. Regresión en [navegador](../frontend/tests/e2e/project-creation.spec.ts) y [HTTP/PostgreSQL M1](../src/test/java/com/daraya/atoconstrucciones/M1IntegrationTests.java).

Verificación local de la corrección: **16 pruebas M1 pasan** con PostgreSQL temporal; **11 flujos de navegador pasan** con HTTP controlado (3 nuevos de creación, 3 fundamentos y 5 cuentas/invitaciones). Creación por teclado a 390 px sin desbordamiento horizontal ni violaciones Axe WCAG 2/2.1/2.2 AA. ESLint, TypeScript, transporte Node, build standalone temporal y checks de enlaces/contratos/secretos correctos. La base y el servidor habituales se conservan; no se creó la obra de la captura en datos empresariales. Recargar el frontend de desarrollo; si se usa standalone, reconstruir/reiniciar el frontend. No se requiere reiniciar el backend para esta corrección.

## Pantallas M3 implementadas

[Cuentas y accesos actualizados](23-cuentas-y-accesos.md), autorizados el 13 de octubre: selector por nombre/correo, identidad visible y confirmación, listado de vinculadas/revocadas/desactivadas y revocación desde la lista. Invitación desde ficha y aceptación por enlace, conservada durante login. UUID internos; directorio solo gerencia y candidatas de obra por rol/cartera. Sustituye la corrección inicial de validación de UUID. [ADR 0015](adr/0015-cuentas-y-accesos-sin-identificadores-manuales.md).

[Portal](../frontend/src/features/client/client-workspace.tsx) en `/cliente` y `/cliente/obras/{id}`: selector/ficha, avance verificado, hitos aprobados, contrato/cobros, reportar depósito con selector de comprobante, solicitud/aceptación de extra y documentos propios. Sin campos de IDs técnicos para completar estos flujos. La ruta de obra existente reutiliza esta proyección para cliente.

[Editor de planificación](../frontend/src/features/scheduling/schedule-panel.tsx): lista y formularios para tareas/etapas/hitos/asignaciones/FS, Gantt opcional, guardado explícito del lote y deshacer local. Un 412 mantiene cambios y exige revisar/descartar para cargar revisión vigente. [Progreso](../frontend/src/features/progress/progress-panel.tsx) distingue reporte/verificación y propuesta/aprobación de pesos. Proveedor ve solo agenda asignada. Evidencia de móvil/teclado y límites de accesibilidad en [20](20-entrega-m3.md).

## Pantallas M4 implementadas

Inicio, ATO, galería/ficha, Contacto, Login y aviso público; editor `/publicacion` con textos, foto/consentimiento, preview privado, revisión visual, publicación/retirada y bandeja. Menú móvil usa details nativo; enlace de salto con foco fijo evita desplazar controles. Textos reales requieren aprobación; fallback funcional y plano decorativo SVG no representan una obra real. Publicaciones usan JPEG acotado, dimensiones/sizes y lazy-load excepto hero. Sin imágenes del PDF, contadores ni testimonios inventados. Formulario conserva UUID/body al reintentar, anuncia error y confirma recepción sin prometer correo/lectura. [Pruebas y límites](21-entrega-m4.md).
