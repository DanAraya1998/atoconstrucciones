# 27 — Rediseño público y contenido de muestra

[Índice](README.md) · [Diseño canónico](07-diseno-ux-ui.md) · [Entrega M4](21-entrega-m4.md)

Pedido autorizado el 27/10/2026: mejorar la presentación pública y usar lorem ipsum para los textos. HU-00, BK-025/BK-028, CA-00.6. Es un ajuste de interfaz sobre M4, sin iniciar M5/M6 ni publicar el sitio.

## Resultado y contenido

Inicio usa portada arquitectónica, titular amplio, acento naranja, índice de secciones, presentación de empresa, servicios, galería asimétrica, proceso de muestra y contacto. ATO/Galería/Contacto comparten la dirección editorial; header y footer conservan rutas existentes y añaden acceso directo a servicios. El logo JPG conserva proporción y colores. Fuentes de sistema; sin dependencias nuevas.

El lorem ipsum autorizado se presenta como **Contenido de muestra** cuando `/public/site` no tiene textos aprobados o el backend no responde. Las secciones continúan completas. Los textos reales aprobados conservan prioridad; ningún borrador del API se muestra ni se marca aprobado. Sin aprobación, Inicio/ATO mantienen `noindex` y quedan fuera del sitemap como antes. El proceso de muestra solo aparece en el modo lorem ipsum, sin atribuir una metodología inventada a ATO.

La galería muestra exclusivamente los DTO publicados del backend cuando existen. En su ausencia presenta tres imágenes conceptuales, con advertencia visible y descripción accesible; no se presentan como obras realizadas ni se crean fichas/contratos/consentimientos ficticios. En páginas posteriores vacías no se repiten los conceptos de muestra.

Esto **no implementa el gestor de álbumes independiente** discutido previamente: cargar fotos/autorizar su origen sin una obra mantiene ese alcance pendiente. El rediseño consume el portafolio existente y ofrece una muestra visual cuando está vacío.

## Imágenes y permisos

Habilidad `imagegen`, herramienta integrada `image_gen`, usada para generar tres conceptos originales de arquitectura ficticia. Inspección visual antes de incorporarlos; WebP a 1536×1024, calidad 82, conversión con Sharp ya instalado. No son fotografías de ATO ni copias de los adjuntos. Archivos finales:

- [Exterior](../frontend/public/images/ato-concept-exterior.webp), 312 206 bytes.
- [Interior](../frontend/public/images/ato-concept-interior.webp), 350 208 bytes.
- [Detalle](../frontend/public/images/ato-concept-detail.webp), 341 012 bytes.

Los prompts completos están en [el registro de generación](recursos/landing-image-prompts.json). Los conceptos son assets estáticos de diseño. El optimizador Next solo añade estos **tres paths exactos** al logo permitido; conserva `search: ""`. No admite rutas `/api`, originales privados ni URLs remotas. Las fotos públicas aprobadas continúan `unoptimized` y conservan el control `no-store`/retirada del backend. No cambian RBAC, aprobación editorial, autenticación, consentimientos, datos ni contratos OpenAPI.

## Estados y validación

Sin textos aprobados: muestra lorem ipsum y `noindex`. Borrador recibido: no se renderiza su contenido. Backend no disponible: muestra la composición de ejemplo sin fingir recepción de contacto ni aprobación. Publicación retirada: desaparece del DTO, ficha/asset dejan de estar disponibles; no se crea una copia alternativa mediante el optimizador. El formulario de contacto conserva la validación y el requisito de aviso aprobado.

La navegación usa enlaces/anclas nativas, targets táctiles, foco visible y menú móvil. CSS de la landing está acotado por componentes; formularios/escala de los paneles privados permanecen conservados. Las transiciones y aparición inicial respetan `prefers-reduced-motion`.

## Ensayo reproducible

```bash
python3 scripts/prepare-landing-test.py
npm run build --prefix target/landing-frontend
```

Desde `frontend/`:

```bash
ATO_CHROME_PATH=/ruta/chrome ./node_modules/.bin/playwright test --config playwright.landing.config.ts tests/e2e/landing.spec.ts tests/e2e/foundations.spec.ts
```

El config levanta un API sintético de presentación en `127.0.0.1:9355` y el standalone aislado en `127.0.0.1:3345`, un worker, sin usar PostgreSQL/Keycloak/Drive/Gmail. El fixture solo entrega datos ficticios y está fuera del código de la aplicación. Las pruebas cambian escenarios en ese servidor, no escriben datos empresariales. No usar este artefacto con su backend de prueba como release habitual.

Para ver los cambios en el entorno habitual: recargar Next en desarrollo, o ejecutar `npm run build --prefix frontend` y reiniciar `npm run start --prefix frontend` con el origen habitual. No necesita migración ni reinicio del backend.

## Resultados ejecutados

Ensayo local del 27/10/2026:

| Comprobación | Resultado |
|---|---|
| Frontend | Build standalone Next 16.4.3, ESLint, TypeScript estricto y suites Node de fechas/transporte correctos; sin nuevas dependencias |
| Navegador | **Ocho flujos pasan**: cinco de landing/estados/contenido/publicaciones y tres de fundamentos. Imagen principal cargada con `fetchpriority=high`; resto de imágenes cargadas antes de las capturas completas |
| Móvil/escritorio/teclado | Capturas revisadas a 1440×1000 y 360×800; sin scroll horizontal. Menú, salto al contenido, anclas y navegación Galería/Contacto funcionan. Encabezados y CTA conservan destinos funcionales |
| Accesibilidad | Cero violaciones Axe WCAG 2/2.1/2.2 AA en Inicio desktop y seis rutas móviles; movimiento reducido activo. No constituye certificación ni ensayo con lector de pantalla |
| Estado y privacidad | Lorem visible con borrador/503, borradores no expuestos, textos aprobados prioritarios, fichas/asset retirados dejan de responder y optimizador rechaza `/api/v1/public/assets/...` |
| Lighthouse móvil local, modo lorem | Rendimiento **96/100**, accesibilidad **100/100**, SEO **69/100**. El único audit SEO ponderado fallido es `noindex`, conservado intencionalmente para la muestra sin aprobar. **LCP 2,70 s**, **CLS 0**, **TBT 33 ms**; prioridad de descubrimiento de portada correcta |
| Contratos/documentación | OpenAPI conservado contra M3, enlaces/secretos y cuatro pruebas del checker correctos; no hay cambios backend ni migraciones |

LCP supera por 0,20 s el objetivo de 2,5 s de T-PERF en este laboratorio móvil simulado; queda como objetivo pendiente de revalidar con contenido definitivo y hosting representativo, sin afirmar una aceptación productiva de rendimiento. TBT no mide INP de campo. No se ejecutaron CI remota, despliegue, servicios externos reales ni suites backend nuevas para este cambio de interfaz.

Capturas completas y portadas se conservan fuera de Git en `target/landing-preview/`: `escritorio.png`, `escritorio-portada.png`, `movil.png`, `movil-portada.png`. Lighthouse conserva JSON y métricas en `target/landing-quality/`. El medidor existente admite `ATO_MEASURE_URL` (solo HTTP de loopback) y `ATO_MEASURE_FOLDER`, manteniendo los defaults M4. Servicios de prueba detenidos al finalizar.
