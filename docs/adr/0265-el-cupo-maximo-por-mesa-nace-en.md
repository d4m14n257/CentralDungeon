---
adr: 265
estado: Aceptada
fecha: 2026-09-21
---

# ADR-0265: El cupo máximo por mesa nace en F3.5, porque un límite que no existe no se puede ajustar

## Decisión

`tables.max_players_cap` arranca en 12 y se aplica en `GameTableService`, en el único punto por el que pasan las tres puertas de escritura (`create`, `createUnassigned` vía `buildTable`, y `update`). Responde `400 MAX_PLAYERS_ABOVE_CAP` con el tope en los parámetros. **No achica nada de lo que ya existe**: una mesa por encima del tope sigue viva y lo que se rechaza es la próxima edición

## Por qué

#141 enumera «cupo máximo» entre los valores que la comunidad quiere mover sin desplegar, y al ir a mudarlo no había nada que mudar: `maxPlayers` era `@Positive` y nada más, así que se podía crear una mesa para dos mil millones de personas. Un ajuste sobre un límite inexistente no es configuración, es una perilla desconectada. Se escribe en el service y no como `@Max` en los dos `record` por la misma razón que todo lo demás de esta fase: una anotación es una constante, y el número tiene que poder cambiar sin desplegar. Y gatea la escritura y no la lectura a propósito: bajar el tope no puede dejar mesas en un estado que crearlas nunca habría alcanzado, que es la forma de #175 aplicada a un límite
