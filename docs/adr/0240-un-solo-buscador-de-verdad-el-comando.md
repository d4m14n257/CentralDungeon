---
adr: 240
estado: Aceptada
fecha: 2026-09-13
modificada-por: [268]
---

# ADR-0240: Un solo buscador de verdad: el comando se escribe, y nada se cierra hasta Enter

> Amplía #164 y #239

## Decisión

`SearchQueryValue` pierde `activeField`: elegir un comando de la lista **escribe el mismo texto** que tipearlo a mano (`/user_name `), y **Enter** es lo único que cierra lo escrito en chips. La lista de valores de un comando de opciones fijas la dispara **el texto** (`openValueOf`) y no un chip fijo, así que `/file_type ` tipeado a mano ofrece la misma lista que elegirlo; lo que entra en la caja es la etiqueta, y `toTerms` resuelve el valor que viaja. El cableado alrededor de la caja —estado, string canónico, debounce, escritura del `?q=`— pasa a `useSearchQuery`, y cada feature declara sus comandos una vez (`userSearchFields`, `myFileSearchFields`, `adminFileSearchFields`) con `examples?` para la ayuda. `basics.search` recibe los `SearchField` de la caja que la abrió (`HelpLink` → `HelpDialog` → `Body`) y arma con ellos la lista de comandos y **todos** los ejemplos. Un comando con lista vacía deja de ofrecerse.

## Por qué

Los dos buscadores de la app se sentían dos idiomas, y no por lo que hacían sino por **cómo se entraba el comando**: elegirlo de la lista fijaba el chip al instante, tipearlo entero lo dejaba como texto hasta Enter. Cuál de las dos cosas hacía cada uno dependía de algo tan tonto como el largo del comando —`user_name` se tipea, `file_categories` se elige—, así que la misma consulta llegaba a dos estados distintos según la pantalla que te lo enseñó. Se estandarizó hacia **el texto**: hay una sola cosa que se está editando, se corrige con las teclas de siempre, y elegir de la lista es autocompletado y no un modo aparte. El chip fijo era el que obligaba a que existieran dos caminos, y con él se fue `activeField`. Las opciones fijas de #239 **no se perdieron**, que era el riesgo real de cerrar hasta Enter: se disparan desde el texto, que es además lo que hace que tipear el comando a mano ofrezca la lista, cosa que antes no pasaba. Y la ayuda: era la misma para todos, con cinco ejemplos fijos sobre `/user_name` y `/discord_name`, así que en `/my/files` enseñaba dos comandos que ahí no existen y ninguno de los tres que sí. Las reglas del lenguaje siguen siendo las mismas en todos lados —son el lenguaje—; los ejemplos son los de la caja que preguntó. La regla `naming` dejó de citar comandos de otra pantalla por lo mismo. Verificado con `npx vitest run` (239, con casos nuevos de `toTerms`, `openValueOf`, `searchQueryOf` y de la ayuda generada), `npx tsc -b`, y a mano en `/my/files` y `/admin/files` contra el backend real. **Corregido (#268)**: el debounce que este cableado centralizaba ya no existe — `useSearchQuery` guarda aparte la última consulta confirmada, y la búsqueda sale con un segundo Enter sobre el texto vacío.
