---
adr: 173
estado: Aceptada
fecha: 2026-09-02
modificada-por: [271]
---

# ADR-0173: Cómo se pagina cada cosa

## Decisión

**Sigue siendo paginación por offset** (`PageResponse` con `page`/`size`/`totalElements`): la comunidad tiene decenas o cientos de mesas, no millones, y el total es justamente lo que las pantallas de admin necesitan mostrar. **Dos presentaciones, según para qué es la lista**: los listados de lectura —explorador, mis mesas, notificaciones, resultados de un buscador— traen más con **"Ver más"** (`useInfiniteQuery`), y las listas de trabajo —`/admin/tables` y las bandejas que vengan— usan **página numerada con anterior/siguiente y el total a la vista**. Tamaños en `config/pagination.ts` (explorador 12, listas 20, admin 25, selector 8). Del lado del servidor, dos reglas duras: **el tamaño de página se topa en 100** (`spring.data.web.pageable.max-page-size`), y **todo endpoint paginado declara su orden por defecto con `@PageableDefault`, con desempate por `id`**

## Por qué

Offset y no keyset porque el problema que keyset resuelve —el `OFFSET` grande— aparece con volúmenes que este sistema no va a tener, y a cambio se pierde el total y el salto de página, que sí se usan hoy. El corte entre las dos presentaciones es qué hace la persona: quien **explora** mira hasta que algo le interesa y "Ver más" no le corta el hilo; quien **modera** trabaja una cola, necesita saber cuánto falta y volver a donde estaba, y ahí la página numerada es la que informa. Scroll infinito no, en ninguno de los dos: se dispara solo, deja el pie de página inalcanzable y nunca dice cuánto falta. El tope de 100 evita que un `?size=100000` convierta cualquier listado en un volcado de tabla. Y el orden por defecto es la lección de #171 escrita como regla: sin `ORDER BY` las páginas pueden repetir o saltear filas, y el desempate por `id` la cierra cuando dos filas comparten `created_at`
