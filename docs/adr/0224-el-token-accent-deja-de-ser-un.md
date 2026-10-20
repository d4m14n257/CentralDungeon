---
adr: 224
estado: Aceptada
fecha: 2026-09-09
---

# ADR-0224: El token accent deja de ser un alias de raised, y el cursor vuelve al preflight

## Decisión

**Tres correcciones al puente de shadcn.** (a) **`--color-accent` pasa a ser `var(--color-border-strong)`** en `design/build.py`, en vez de `var(--color-raised)`. `accent` es la superficie de *hover y selección*, y aliasarla a `raised` la volvía invisible sobre cualquier superficie elevada — que es donde vive: menús, popovers, panel de notificaciones. (b) **`styles/base.css` repone `cursor: pointer`** en todo `<button>` no deshabilitado: Tailwind 4 lo sacó de su preflight. (c) **Dos reglas sin `@layer`** sobre los `data-slot` de shadcn — cursor en los ítems de menú (vienen con `cursor-default`) y `border-strong` en el separador (viene con `bg-border`, que en oscuro es el mismo tono que el popover). Van sin capa a propósito: una regla sin capa le gana a cualquier utilidad de Tailwind, y estas tienen que ganarle a clases que trae el componente generado, que no se edita a mano

## Por qué

Lo reportó el usuario sobre el menú de la cuenta: el avatar no se veía clickeable, el menú desplegado no mostraba qué opción estaba señalando, y los separadores no existían en oscuro. Medido en el navegador antes de tocar nada, y los tres salían del mismo lugar: en oscuro `--color-accent`, `--color-raised`, `--color-popover` y `--color-border` valen **todos** `#1b2438`. Así que `focus:bg-accent` pintaba exactamente el fondo sobre el que estaba, y `bg-border` del separador también. **No era solo del tema oscuro**: en claro `accent` y `popover` también coinciden en `#f1f5f9`. **#157 ya había encontrado esto y lo parcheó fila por fila** en los tres lugares que dibujan una notificación, eligiendo `border-strong` a mano. Esta vez se arregla en el token, que es donde tenía que estar: el tono que #157 eligió es el que ahora sale del design system, y los dos `focus:bg-border-strong!` del `NotificationBell` se borran por duplicados — verificado midiendo que el hover del panel queda idéntico (`rgb(43,56,84)`). El paso de hover mide **1.32:1 en oscuro y 1.36:1 en claro** sobre una superficie elevada, y 1.54:1 / 1.48:1 sobre `surface`: los dos temas quedan con el mismo salto, que es justamente lo que un token da y un parche por pantalla no. Las 34 mediciones de contraste de `build.py` siguen en AA.
