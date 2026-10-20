---
adr: 232
estado: Aceptada
fecha: 2026-09-10
---

# ADR-0232: Dónde se usa un archivo se deriva del vínculo; a qué flujo pertenece se guarda.

> Son dos preguntas, no una

## Decisión

`usages` sale de tres consultas agrupadas por página —`table_files`, `task_files`, `submission_files`— y dice dónde está vinculado **ahora**. La pertenencia a un cajón vive en `file_categories` y dice dónde perteneció **alguna vez** (#233)

## Por qué

La distinción es fácil de perder y es la que sostiene todo el diseño. Despegar un archivo de una mesa cambia lo primero y no toca lo segundo: el archivo **fue** material de esa mesa y lo sigue habiendo sido. Por eso uno se deriva —siempre está bien, no se puede desincronizar, y es lo que #11 eligió para los contadores de jugadores— y el otro se guarda, porque no hay de dónde derivarlo una vez que el vínculo se fue. **Dos consultas se volvieron tres** al entrar `task_files`, y sigue siendo constante: lo que importa no es el número sino que no sea una por fila. La cuarta, `registration_files`, entra en F2. **Efecto lateral que era el que más importaba**: el vacío es la respuesta útil. Un archivo que no aparece en ningún lado es el que la purga de #75 va a alcanzar primero, y decirlo —«sin usar»— avisa sin explicar la retención.
