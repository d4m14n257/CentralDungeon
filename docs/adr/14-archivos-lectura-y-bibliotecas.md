---
adr: 14
titulo: "Archivos: lectura y bibliotecas"
estado: Vigente
actualizada: 2026-10-20
absorbe: [79, 205, 206, 207, 211, 232, 233, 237, 241, 242, 278, 279, 282]
---

# ADR-14: Archivos: lectura y bibliotecas

> Quién puede leer un archivo, cómo se sabe dónde se usa y a qué flujo pertenece, y las dos bibliotecas —la personal en `/my/files` y la de la plataforma en `/admin/files`—. El modelo y el almacenamiento de los archivos son de otra ADR; las reglas de código, de `arquitectura` §2 y §4. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Un archivo de la biblioteca se vincula a la mesa, no se copia

**Decisión.** Un master puede usar en su mesa un archivo publicado por la plataforma sin copiarlo. Quitarlo de la mesa no borra el archivo global, y actualizar el archivo global actualiza lo que ven todas las mesas que lo usan. Lo que una mesa ya agregó lo conserva aunque después la plataforma lo oculte (§7).

**Por qué.** Es la razón de ser de lo publicado (ADR-13 §2): la hoja de personaje por defecto existe para que cada master no tenga que subir la suya. Como se vincula y no se duplica (ADR-13 §2), una sola fila sirve a todas las mesas.

*Antes: #79.*

## §2 Leer un archivo pasa por una sola puerta, y lo que alguien ve en pantalla lo puede abrir

**Decisión.** Toda lectura pasa por `FileService.requireReadable`, que acumula vías: el dueño; lo publicado; quien dirige una mesa que lo tiene vinculado (y un admin, en toda mesa); **cualquiera que pueda ver la mesa** lee lo que la mesa comparte, sin exigir pertenencia, salvo el vetado (ADR-09 §4); **quien dirige la mesa** abre lo que le entregaron a una petición suya; quien una petición alcanza abre el formulario que el master le adjuntó (ADR-15 §3); y quien dirige la mesa abre lo adjunto a una postulación. Lo privado no llega nunca por la vía de la mesa. El repositorio resuelve qué mesas alcanza el archivo; el service decide la autorización con `masterService.isMasterOf` (ADR-11 §1, ADR-05 §5, ADR-06 §4).

**Por qué.** Cada vía nueva nació del mismo desajuste: una pantalla lista un archivo y la descarga responde `404`, que es lo peor de los dos mundos. Lo que un master adjunta a su mesa abierta —la hoja, las reglas de la casa— lo necesita sobre todo quien **todavía decide si postularse** (ADR-13 §1). Quien entregó ya llega por la primera vía, así que la de las entregas es solo para quien dirige.

*Antes: #206, #211.*

## §3 Dónde se usa un archivo se deriva del vínculo; a qué flujo pertenece se guarda

**Decisión.** `usages` dice dónde está vinculado **ahora** y sale de una consulta agrupada por página por cada tabla de vínculo —`table_files`, `task_files`, `submission_files`, `registration_files`—, nunca una por fila. La pertenencia a un cajón vive en `file_categories` (§4) y dice dónde perteneció **alguna vez**. Un archivo sin usos se muestra como «sin usar».

**Por qué.** Despegar un archivo de una mesa cambia lo primero y no lo segundo: el archivo **fue** material de esa mesa. Por eso uno se deriva —no se puede desincronizar, como los contadores de ADR-03 §1— y el otro se guarda, porque cuando el vínculo se va no queda de dónde derivarlo. El vacío es la respuesta útil: lo que no se usa en ningún lado es lo primero que alcanza la purga (ADR-13 §3), y decirlo avisa sin explicar la retención.

*Antes: #232.*

## §4 El cajón de un archivo es el flujo del que viene, y es una relación que solo crece

**Decisión.** Cinco cajones: `TableMaterial`, `MasterRequest`, `PlayerApplication`, `PlayerSubmission`, `Announcement`. Viven en la tabla `file_categories`, add-only, y los escribe el servicio que hace el vínculo: adjuntar a una mesa, responder una petición, adjuntar un formulario a un pedido. Solo pregunta el cajón quien no tiene flujo que observar: `/my/files` y la biblioteca de la plataforma (§7). No hay audiencia: `public_audience` (ADR-13 §2) no existe. El selector de archivos de un flujo pide un cajón y recibe **todos** los publicados de ese cajón junto con los propios, en la misma forma, sin filtrar por el rol de quien elige.

**Por qué.** Nadie al subir puede declarar honestamente «esto es una plantilla de ficha»; lo único que el sistema sabe con certeza es **desde dónde se subió**. Es tabla y no columna porque un archivo pertenece a todos los flujos en que se usó, y la deduplicación (ADR-13 §3) vuelve eso el caso normal. La pertenencia no se revoca: despegar no dice que nunca fue material de la mesa. La audiencia se fue porque el flujo ya dice a quién sirve, y dos ejes decidiendo lo mismo terminan discrepando: la hoja de personaje publicada para jugadores no aparecía en el selector del master que la adjunta. Quién termina leyendo un archivo no es quién lo adjunta.

*Antes: #205, #233.*

## §5 La biblioteca personal ofrece los cajones que los roles y la pertenencia de cada uno pueden llenar

**Decisión.** `/my/files` es solo lo personal. `PlayerApplication` y `PlayerSubmission` son de quien tiene `Player`; `TableMaterial` y `MasterRequest`, de quien tiene `Master` **o dirige alguna mesa** (ADR-06 §4). `Announcement` no aparece nunca. Lo decide `FileService.personalCategoriesOf`, lo expone `GET /files/mine/categories`, y una petición que nombra otro cajón se rechaza (ADR-05 §5). Quien no es `Player` ni `Master` ni dirige mesas no tiene biblioteca: el ítem «Mis archivos» no está en su menú (`useHasPersonalLibrary`, la misma forma que `useAvailableContexts`), y si entra por URL la pantalla dice por qué. No es un guard de ruta.

**Por qué.** Ofrecer los cinco cajones a todos dejaba a un jugador archivar material de mesa y a un anuncio vivir en la biblioteca de una persona. Una biblioteca propia la llenan los flujos de jugador y de master; una cuenta que no pasa por ninguno no tiene qué guardar. Se decide por rol, como todo lo que la shell muestra, porque `me.roles` decide qué mostrar y nunca qué permitir (ADR-20 §1); los roles se acumulan, así que se pregunta si tiene el rol y no si tiene solo ese. Que toda cuenta nazca `Player` (ADR-05 §1) es una afirmación sobre el alta, no un invariante: un rol se revoca.

*Antes: #237, #241.*

## §6 En la biblioteca personal el buscador es el único filtro

**Decisión.** `/my/files` no tiene fila de toggles de cajón ni `?category=`: el cajón se acota con `/file_categories` desde el buscador (ADR-22 §1). El placeholder de los buscadores de archivos dice «Buscar por nombre», que es el criterio por defecto (`FileSearchField.NAME`); la leyenda de abajo es la que dice que existe la barra. `/admin/files` conserva sus toggles de cajón.

**Por qué.** Los toggles preguntaban algo que el buscador contesta mejor: `/file_categories` se combina con el nombre, con el tipo y con `/or`, y cada fila ya lleva su cajón como badge. Un placeholder dice qué busca si no le decís nada; listar comandos es trabajo de la ayuda y de las sugerencias. En `/admin/files` los cajones son el catálogo de la plataforma y la pantalla es una tabla de moderación, con otras necesidades.

*Antes: #242.*

## §7 La biblioteca de la plataforma solo tiene lo de la plataforma, con su ciclo de vida

**Decisión.** `/admin/files` lista solo archivos de la plataforma —`file_type` `Library` o `Public`, fijo en el `WHERE`—, nunca los privados de las personas, con la columna de usos y la columna «En la biblioteca». Estados: `Library` sin `published_at` es *sin publicar*, `Public` es *publicado*, `Library` con `published_at` es *oculto*, `status = Deleted` es *dado de baja*; el frontend deriva el estado (`libraryStateOf`). Subir (`POST /admin/files`, `LibraryUploadRequest {categories, publish}`) exige cajón y publica si se pide; una resubida (ADR-13 §3) nunca oculta. Publicar exige cajón (`FILE_NEEDS_CATEGORY`) y sella `published_at` la primera vez; ocultar vuelve a `Library` sin sacarlo de la biblioteca; cambiar el cajón solo sin publicar u oculto, y es la única escritura que quita una membresía (solo de los tres publicables). Toda acción se confirma. Los cajones publicables son tres —`TableMaterial`, `MasterRequest`, `Announcement`—: filtrar por uno del lado jugador es `400`, y una membresía de ese lado que el archivo arrastre no se muestra. Un archivo de la plataforma no está en la biblioteca personal de quien lo subió, su dueño no lo cambia ni lo da de baja, y la purga no lo toca. `deleteAsAdmin` sobre un privado ajeno es `404`.

**Por qué.** Mezclar la biblioteca con «todo lo que subió cualquiera» metía postulaciones de jugadores en una pantalla de administración. Ver o dar de baja el archivo privado de alguien se hace **personificándolo**, no desde acá. Subir sin publicar es revisar antes de que los masters lo vean; ocultar no puede devolver el archivo a quien lo subió, porque para un admin sin biblioteca personal (§5) eso es hacerlo desaparecer. *Oculto* no es otro `file_type`: se comporta igual que sin publicar, y haber estado publicado es un hecho con fecha.

*Antes: #207, #278, #282.*

## §8 Subir a la biblioteca es una página propia, y cada archivo elige su cajón

**Decisión.** La cabecera de `/admin/files` enlaza a **`/admin/files/upload`**: zona de subida con `multiple`, la misma `StagedFileList` de todos los flujos con un `PublishCategorySelect` por fila (los tres cajones publicables, sin preselección), y al pie «Cancelar», «Subir sin publicar» y «Subir y publicar», cada uno con su confirmación diciendo qué ven los masters. Sin cajón en todos los archivos no se llega a confirmar: el botón dice cuántos faltan y marca los selects vacíos (`aria-invalid`), no se deshabilita en silencio. Cada archivo sube con **un** cajón. Al terminar vuelve a la lista; lo que falló queda listado con su cajón.

**Por qué.** Cada archivo es un documento distinto —la hoja en blanco es material de mesa, el reglamento es un anuncio—, así que elegir el cajón para todos a la vez no es cómo se piensa una subida. Un apartado exclusivo es una página, con su URL y el «atrás» del navegador. Un select y no casillas porque un archivo es una cosa; ponerlo en otro cajón es volver a subirlo, que no guarda otra copia (ADR-13 §3).

*Antes: #279.*
