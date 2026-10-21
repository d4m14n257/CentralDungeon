---
adr: 22
titulo: Buscadores
estado: Vigente
actualizada: 2026-10-20
absorbe: [164, 221, 239, 240, 268]
---

# ADR-22: Buscadores

> El lenguaje de búsqueda de una línea que hablan todos los buscadores de la app, cómo se edita y cuándo busca, y el alcance del buscador de personas. Las reglas de código están en `arquitectura` §2 y §3, y la forma visual en `diseno`. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Todo buscador habla el mismo lenguaje de una línea

**Decisión.** Texto suelto busca por el **criterio básico** del endpoint; un prefijo `/campo` busca por ese campo. **La barra es el único separador**: `/and` y `/or` unen criterios, y un «and» o un «or» escritos sueltos son parte del valor. Las **comas separan alternativas del mismo criterio** (`/user_name damian,carlos,daniel`). Los conectores se evalúan **de izquierda a derecha y sin precedencia** (`a /or b /and c` es `(a or b) and c`). Todo lo que sigue a un comando, espacios incluidos, es su valor hasta la próxima `/`. Un `/token` que no es un campo conocido **queda como texto literal**, y un campo sin valor no busca nada: un buscador nunca falla mientras se escribe. El parser vive dos veces —`common/search/SearchQueryParser.java` decide qué devuelve la consulta, `lib/searchQuery.ts` pinta los chips— con los mismos casos en tests de los dos lados, y el frontend manda la consulta canónica en `?q=`, con el conector explícito y las alternativas por coma. La consulta se arma con Criteria API. Para usuarios, el criterio básico son **`/discord_name` y `/user_name` a la vez**.

**Por qué.** El costo de que cada buscador invente su sintaxis se paga cuando hay cinco. Si «or» suelto fuera una instrucción, buscar a «Nestor Ortiz» sería imposible de escribir. Las comas responden a «quiero a estos tres» sin repetir el campo. Sin precedencia porque los chips se leen en orden, y una precedencia invisible haría que lo que se ve y lo que se busca dejen de coincidir. Un prefijo desconocido como literal en vez de `400` deja escribir de a una tecla y evita que las dos copias del parser discrepen en un error. A una persona se la busca por el nombre que se sabe, sin saber en qué columna lo guarda el sistema. La forma de la consulta recién se conoce en tiempo de ejecución, así que ni un query derivado ni un `@Query` fijo alcanzan.

*Antes: #164.*

## §2 Los comandos llevan su entidad adelante, y los de valores fijos ofrecen la lista

**Decisión.** Los comandos van en inglés y nombran su entidad: `discord_name`, `user_name`, `file_name`, `file_owner`, `file_type`, `file_categories`, `catalog_name`. Un `SearchField` con `values` ofrece la lista al escribir su comando, y en la caja entra la etiqueta mientras viaja el valor; un comando con lista vacía no se ofrece. En la lista de sugerencias el comando va primero y solo junto a otro comando, nunca al lado de un valor. `/and` y `/or` se ofrecen apenas hay un criterio que unir, no antes, y el chip del conector elegido se ve al instante.

**Por qué.** Con `name`, `owner`, `type` a secas, el mismo `/name` buscaba cosas distintas según la pantalla, y quien lo aprendía una vez se equivocaba la mitad de las veces. Un nombre de archivo se escribe porque nadie puede listarlos, pero un formato se elige: nadie va a tipear un MIME de Word. Poner `/application/pdf` junto a «PDF» enseñaría algo falso y metería el MIME en el nombre accesible de la opción. Unir nada no significa nada, y un conector elegido que no se ve parece no haber hecho nada.

*Antes: #239.*

## §3 El comando se escribe como texto, y un criterio se cierra en chip con Enter

**Decisión.** Elegir un comando de la lista **escribe el mismo texto** que tipearlo (`/user_name `); no hay un campo fijo aparte. La lista de valores de un comando de opciones fijas la dispara el texto, así que tipearlo a mano ofrece lo mismo que elegirlo. Las flechas eligen sugerencia y Enter o Tab la confirman; Enter sin lista abierta cierra lo escrito en un **chip** con su campo a la vista, su `×` y el conector con el anterior clickeable para pasar de «y» a «o»; Escape cierra la lista sin cerrar el diálogo que la aloja; Backspace con el texto vacío devuelve el último chip al input. El cableado —estado, string canónico, `?q=`— está en `useSearchQuery`, y cada feature declara sus comandos una vez (por ejemplo `explorerSearchFields`, `adminTableSearchFields`, `userSearchFields`, `adminUserSearchFields`, `myFileSearchFields`, `adminFileSearchFields`, `approvalRequestSearchFields`; cada uno en el `searchFields.ts` de su feature) con `examples`. La ayuda `basics.search` recibe los comandos de la caja que la abrió y arma con ellos la lista y todos los ejemplos.

**Por qué.** Si elegir fija un chip y tipear deja texto, la misma consulta llega a dos estados según la pantalla: los buscadores se sentían dos idiomas. Con el texto hay una sola cosa que se edita, con las teclas de siempre, y elegir es autocompletado y no un modo aparte. Los chips son lo que hace descubrible la parte avanzada: nadie adivina `/tag`, pero todos entienden un chip que dice «Discord: pablo». Una ayuda fija enseñaba en `/my/files` comandos que ahí no existen; las reglas del lenguaje son las mismas en todos lados, los ejemplos son los de la caja que preguntó.

*Antes: #240.*

## §4 Se busca con un segundo Enter, nunca mientras se escribe, y todo buscador puede limpiar

**Decisión.** El primer Enter cierra lo escrito en un chip y no busca; **el segundo, con el texto vacío, busca los chips**. Armar la consulta —elegir un comando, escribir, quitar un chip, cambiar un conector— no busca: lo que viaja al servidor y al `?q=` es la **última consulta confirmada**, que `useSearchQuery` guarda aparte. Mientras los chips difieren de lo buscado, el borde de la caja pasa al color de marca y aparece un botón «↵ Buscar»; «Enter para buscar» queda para lectores de pantalla. No hay debounce, y el `?q=` solo se escribe cuando una búsqueda cambia la consulta. **«Limpiar filtros»** vive en `SearchQueryInput`, aparece solo si hay algo que limpiar, busca sin esperar Enter y vuelve a la búsqueda por defecto de la pantalla (`defaultQuery`), incluidos los filtros vecinos (`extraFiltersActive`/`onClearExtraFilters`). No hablan este lenguaje el `<Input>` de `/admin/catalogs` ni los combobox de elegir un valor (`CatalogCombobox`, `CanonicalPicker`, `FilePicker`), donde buscar al escribir es la función.

**Por qué.** Buscar mientras se escribe mandaba `/user_name d`, `da`, `dam` al servidor mientras la persona todavía elegía, y la lista saltaba con resultados de algo que nadie preguntó; un debounce solo lo espacia. Un Enter y no dos buscaba al cerrar el primer criterio, cuando quien va a sumar otro no quiere esa búsqueda. La misma regla para el texto suelto: «se busca lo que está en chips» se explica en una línea. Sin aviso, la lista parece responder a los chips que se ven cuando responde a los anteriores. Limpiar es la única acción que ya dice qué se quiere buscar.

*Antes: #268.*

## §5 El buscador de personas para sumar co-masters se acota a la mesa

**Decisión.** `GET /api/v1/game-tables/{id}/master-candidates`, autorizado por pertenencia: el service verifica que el actor sea el `Primary` de **esa** mesa. `GET /api/v1/users/search` sigue siendo solo de admin. `UserPicker` recibe un `tableId` opcional —un id, no una entidad (`arquitectura` §3.1.5)— y la clave de caché `queryKeys.users.search` lleva el alcance.

**Por qué.** Abrir el directorio con `hasAnyRole('MASTER', …)` lo dejaría accesible a cualquiera con el rol, tenga o no una mesa que administrar, y fallaría a un `Primary` asignado por un admin que nunca tuvo el rol (ADR-06 §7, §4). Acotado a la mesa solo responde a quien efectivamente puede agregar un master. El directorio y la búsqueda de una mesa son respuestas distintas a las mismas palabras y no pueden compartir caché.

*Antes: #221.*
