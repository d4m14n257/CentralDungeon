---
adr: 226
estado: Aceptada
fecha: 2026-09-09
modificada-por: [229]
---

# ADR-0226: Una mesa no se crea sin decir qué, dónde y cuándo; y los formularios hablan el idioma de quien los llena

## Decisión

**Cuatro cosas, de un mismo recorrido: crear una mesa.** (a) **Regla de negocio nueva**: `create` y `update` exigen **al menos un sistema, al menos una plataforma y al menos una franja de agenda**, con un código por cada una (`TABLE_NEEDS_SYSTEM`, `TABLE_NEEDS_PLATFORM`, `TABLE_NEEDS_SCHEDULE`). **Los tags siguen opcionales**: son etiquetas libres que ayudan a encontrar la mesa, no datos que la definan, y obligar a poner una produce tags de relleno que después un admin tiene que fusionar (#59). El wizard corta en el paso que puede arreglarlo, no al final. (b) **Los mensajes de zod se traducen**, con un `customError` global en `config/zodErrors.ts`: antes decían «Too small: expected string to have >=1 characters», en inglés y escrito por la librería. (c) **Los días de la semana van con mayúscula inicial en los dos idiomas**, capitalizando con el locale y no con `toUpperCase()`. (d) Dos helpers compartidos en `e2e/helpers/` reemplazan las seis copias del recorrido del wizard

## Por qué

Todo salió de que el usuario creara una mesa y mirara. **(a)** #196 había dejado esta puerta abierta con nombre y apellido — *«si más adelante se decide que una mesa no puede abrir sin calendario, es una validación de `CreateGameTableRequest` y no un efecto silencioso de `approve()`»* — así que la regla aterriza exactamente donde esa decisión dijo. Va en el backend y no solo en el wizard porque es una regla de negocio, no una comodidad de formulario. **No aplica a `createUnassigned`**: la mesa que crea un admin sin master es un borrador con nombre y nada más, a propósito (#72). Y **el orden importa**: la validación va después de autorizar, o quien no dirige la mesa recibiría un `400` sobre su agenda en vez de un `403` — lo delató un test que ya existía. **(b)** El mensaje de zod se veía igual en español y en inglés, que es el mismo incumplimiento de la regla dura 18 que #197 persigue en el backend; se había colado porque nadie había mirado un formulario con el idioma cambiado. Un mapa global y no un mensaje por regla: un esquema describe qué es válido, y acordarse de un `t()` en cada `.min()` es exactamente cómo la mitad vuelve a quedar en inglés. La traducción ocurre **cuando la regla falla**, así que un esquema definido al cargar el módulo igual sale en el idioma vigente. **(c)** `Intl` escribe cada idioma con su ortografía y el español pone los días en minúscula. Se capitaliza igual: un día acá es una etiqueta de un selector y un encabezado de una agenda, nunca una palabra dentro de una oración, y tener las dos formas hacía que la misma lista se leyera distinto según el idioma. **(d)** La regla nueva rompió las seis copias del recorrido del wizard de una sola vez, que es lo que pasa cuando el mismo camino está escrito seis veces. Al arreglarlas apareció una interacción real: **dos mesas del mismo master ya no pueden nacer con la misma franja**, porque ahora toda mesa lleva agenda y R1 de #178 las compara — el helper acepta el día justamente por eso.
