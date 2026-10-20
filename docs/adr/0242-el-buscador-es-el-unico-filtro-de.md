---
adr: 242
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0242: El buscador es el único filtro de /my/files, y el placeholder nombra el criterio básico

## Decisión

Se quita la fila de toggles de cajón (`FileCategoryFilter`) de `/my/files`, y con ella el parámetro `?category=` de esa pantalla: el cajón se narrowea con `/file_categories`, desde la misma línea que todo lo demás. Los placeholders de los dos buscadores de archivos pasan a decir «Buscar por nombre» a secas.

## Por qué

**Los toggles preguntaban algo que el buscador ya contesta mejor.** `/file_categories` narrowea por cajón desde la misma línea, combinable con el nombre y con el tipo y con un `/or` en el medio, mientras que los toggles eran de a uno y no se cruzaban con nada; y cada fila ya lleva su cajón como badge, así que ver de qué cajón es un archivo nunca dependió del filtro. Dos formas de hacer la misma pregunta, una peor, ocupando el ancho de la pantalla. **El placeholder listaba comandos** —«Buscar por nombre, /file_type o /file_categories»— que es trabajo de la ayuda y de la lista de sugerencias, no de un texto que se borra apenas escribís una letra. Lo que un placeholder tiene que decir es qué busca si no le decís nada, y eso es el nombre del archivo (`FileSearchField.NAME` es el criterio por defecto de las dos pantallas). La leyenda de abajo sigue diciendo que existe la barra, y ahí sí queda escrito. Sin cambios: la lista ya venía paginada de a 20 por el `@PageableDefault` del backend, más usados primero (#171), con los controles apareciendo desde la segunda página; y ya llevaba divider entre filas (`divide-y` en `FileList`, que en Tailwind 4 es un `border-bottom` en todas menos la última). `/admin/files` conserva sus toggles: ahí los cinco cajones son del catálogo de la plataforma y no de una persona, y esa pantalla es una tabla de moderación con otras necesidades — si conviene alinearla es una decisión aparte.
