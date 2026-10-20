---
adr: 168
estado: Reemplazada
fecha: 2026-09-02
reemplazada-por: [231]
---

# ADR-0168: La ayuda se parte por audiencia y se enlaza por #ref, y cada fase la actualiza — reemplazada por #231: la ayuda pasó a ser un diálogo y /help dejó de ser una pantalla

## Decisión

`/help` deja de ser una pantalla sola: el índice guarda lo que sirve a todos (buscar, contextos, estados de mesa, cuenta, notificaciones) y cada rol tiene la suya — `/help/players`, `/help/masters`, `/help/admins` (con Owner adentro, que puede todo lo de admin). Son **rutas hijas**, no pestañas con `useState`, así cada audiencia tiene URL propia. Cada bloque lleva un **`id` estable** que es su dirección: `/help#search`, `/help/admins#assign-masters`, y desde la aplicación se enlaza al `#ref` exacto — la leyenda del buscador va a `#search`, el diálogo de asignar masters a `#assign-masters`. El `id` es contrato, no maquetado: cambiarlo rompe links que ya existen. El salto lo hace **cada sección**, no la pantalla (`useScrollOnHash`): React Router navega sin recargar, así que el salto nativo del navegador nunca ocurre, y como las páginas cargan con `lazy` un efecto en el padre corre antes de que la sección exista. **La regla nueva**: toda fase que agregue funcionalidad agrega o actualiza su sección de ayuda, y eso entra en la definición de terminado (`plan-desarrollo.md` §6, punto 8)

## Por qué

Una sola página crece hasta que nadie la lee: a un jugador no le sirve leer cómo se aprueba una mesa para encontrar cómo se postula, y el rol de quien lee es el corte natural — es el mismo que ya organiza la navegación (#37, #89, #103). Los `#ref` son lo que hace que la ayuda se use de verdad: enlazar "la ayuda" obliga a buscar dentro, enlazar `#assign-masters` contesta la pregunta que se acaba de hacer. Y la regla de actualizarla en cada etapa existe porque la documentación que se escribe "después" no se escribe: la ayuda es parte de la funcionalidad, no un adorno posterior — E1 y E2 ya están cubiertas acá, y lo que venga entra con la suya
