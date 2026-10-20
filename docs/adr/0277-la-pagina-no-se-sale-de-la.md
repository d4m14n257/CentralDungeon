---
adr: 277
estado: Aceptada
fecha: 2026-09-29
modifica: [138]
---

# ADR-0277: La página no se sale de la pantalla en ningún ancho, de 320 px a un ultrawide

> Precisa #138

## Decisión

**Por arriba**, el header y el `main` de los layouts siguen acotados a `max-w-5xl` (1024 px) y centrados: en 2560 o 3440 px la app no se estira. **Por abajo**, hasta 320 px, que es menos que el mínimo de diseño de 375: la navegación de secciones pasa a un componente, **`SectionNav`**, que **baja de línea** cuando las secciones no entran; el header achica espacios, el padding del chip de contexto y el logo debajo de `sm`, y si aun así no entra, **cede el logo** (`min-w-0 truncate`) antes que el selector, la campana o el avatar. Se verificó midiendo `scrollWidth` contra `innerWidth` en las 21 rutas de los tres contextos, a 320, 360, 375, 400, 768, 1440, 2560 y 3440 px

## Por qué

Lo encontró el usuario revisando el lienzo de catálogos a 400 px: la barra de admin, con ocho secciones, empujaba la página en horizontal en **todas** las pantallas de admin, y el pedido fue que no pase «nunca», recordando que por arriba ya hay límite para ultrawide. Bajar de línea y no scrollear dentro de la barra, porque una sección fuera de la vista es una sección que nadie encuentra. Las tres barras (Player, Master, Admin) repetían las mismas clases a mano, que es lo que #273 manda nombrar: ahora son una. La medición encontró otros dos desbordes que nadie había visto: el header a 320 px en toda la app y una columna del lienzo de catálogos sin `min-w-0`
