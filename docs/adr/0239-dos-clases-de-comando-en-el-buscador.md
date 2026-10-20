---
adr: 239
estado: Aceptada
fecha: 2026-09-12
---

# ADR-0239: Dos clases de comando en el buscador, y los nombres llevan su entidad adelante

> Amplía #164

## Decisión

`SearchField` gana `values?`: un campo que las declara ofrece la lista al elegirlo y el chip muestra la etiqueta, no el valor que viaja. Y los wire names pasan a `file_name`, `file_owner`, `file_type` y `catalog_name`

## Por qué

Dos defectos del mismo buscador. **Los nombres**: `UserSearchField` ya usaba `discord_name` y `user_name`, y archivos y catálogos habían puesto `name`, `owner`, `type` a secas — así que el mismo `/name` buscaba cosas distintas según la pantalla, y quien lo aprendía una vez se equivocaba la mitad de las veces. **Las clases**: un nombre de archivo se escribe porque nadie puede listar todos los nombres, pero un formato se elige, porque son cuatro y nadie va a tipear `application/vnd.openxmlformats-officedocument.wordprocessingml.document` ni tiene por qué saber que existe. Con los valores declarados, elegir el comando muestra la lista. **El comando se muestra al lado de un comando y no de un valor**: poner `/application/pdf` junto a «PDF» enseñaría algo falso, y además metía el MIME en el nombre accesible de la opción. **El comando va primero en la lista**: lo que se elige de ahí es el comando, así que encabeza — se leía al revés, con el comando apagado a la derecha, que es el énfasis equivocado para lo que alguien está ahí para aprender. **`/and` y `/or` siguen ofreciéndose apenas hay un criterio que unir**, y no antes: unir nada no significa nada. **Y el conector elegido se ve al instante**: su chip esperaba a que se escribiera algo más, así que elegir `/and` parecía no hacer nada y el chip aparecía después de la nada — y `or` se mostraba enseguida mientras `and` no, sin razón visible. Ahora el chip está desde que hay un criterio al que unirlo, y dice con qué se va a unir lo próximo. La ayuda de `basics.search` explica las dos clases y cómo se nombran; cada pantalla con buscador lista además **sus** comandos en su propia ayuda. **Corregido (#240)**: la lista de valores la dispara **el texto** y no un chip fijo, así que `/file_type ` tipeado a mano ofrece lo mismo que elegirlo; en la caja entra la etiqueta y el valor que viaja se resuelve al salir. Y los ejemplos de la ayuda dejaron de ser fijos: los arma la caja que la abrió, con sus comandos.
