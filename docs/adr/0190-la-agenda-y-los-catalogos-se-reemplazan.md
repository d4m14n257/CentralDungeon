---
adr: 190
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0190: La agenda y los catálogos se reemplazan como conjunto, y sus filas se marcan

## Decisión

`TableScheduleService.replace` y `TableCatalogService.replaceLinks` reciben el conjunto completo y reconcilian: lo que está y se quiere revive, lo que falta se inserta, lo que sobra se **marca** (`Deleted` / `Removed`), nunca se borra

## Por qué

Dos razones, y la segunda es la que obliga. **(a)** Las reglas son del conjunto: #178 compara la agenda entera, así que validar franja por franja dejaría pasar un par que está bien de a una y roto junto. **(b)** La clave primaria de `table_schedules` es `(mesa, día, hora)` y la de los puentes es `(mesa, valor)`: un master que saca el martes 20:00 y lo vuelve a poner chocaría con la fila que acaba de borrar. Marcando, volver a ponerlo es un `UPDATE`, y de paso queda el registro de lo que la agenda supo ser — que es de donde salieron las sesiones ya materializadas (#26, #33)
