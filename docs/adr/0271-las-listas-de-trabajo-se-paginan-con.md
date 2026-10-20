---
adr: 271
estado: Aceptada
fecha: 2026-09-28
modifica: [173]
---

# ADR-0271: Las listas de trabajo se paginan con tira numerada, salto directo a una página y tamaño elegible

> Precisa #173

## Decisión

`PaginationControls` muestra: «Página X de Y · N en total»; **anterior/siguiente**; una **tira numerada compacta** —la primera, la última y la actual con sus vecinas, con `…` en los huecos (`1 … 899 900 901 … 1000`)—; y un campo **«Ir a…»** que salta con Enter, acotado a `1..Y`. En las seis pantallas de admin (`queue`, `tables`, `catalogs`, `files`, `users`, `requests`) suma un **selector «Por página» con 10 · 25 · 50 · 100** (`adminPageSizeOptions`, default 25). El tamaño vive en **`?size=`** como la página (#185); un tamaño que no está entre los ofrecidos cae al default, y cambiarlo vuelve a la página 1. Con selector, la paginación se ve aunque haya una sola página, mientras haya más filas que el tamaño más chico. Por debajo de `sm` la tira se esconde y quedan «X de Y», las flechas y el salto. `pageSize.adminQueue` pasa a llamarse `pageSize.admin`

## Por qué

Pedido del usuario revisando `/admin/users` y `/admin/files`: con mil usuarios, anterior/siguiente obliga a 899 clics para llegar a la página 900. #173 ya decía que el salto de página se usa y que por eso se eligió offset y no keyset; lo que faltaba era ofrecerlo. El JSDoc viejo del componente argumentaba lo contrario («saltar a la página 7 no significa nada estable»), y no era cierto: el orden de todo endpoint paginado es fijo con desempate por `id` (#173). El 100 es el tope del servidor, así que ofrecer más sería ofrecer un tamaño que el backend achica en silencio. `/my/files` queda sin selector: su tamaño es una decisión fija (#242)
