---
adr: 12
titulo: Catálogos
estado: Vigente
actualizada: 2026-10-20
absorbe: [53, 54, 55, 56, 57, 58, 59, 81, 179, 183, 184, 185, 246, 275, 276]
---

# ADR-12: Catálogos

> Sistemas, tags y plataformas: qué es cada uno, cómo se agrupan los sinónimos, quién los propone y los clasifica, y cómo se administran como grupos. El modelo y sus reglas están en `arquitectura` §4.7; el lienzo de nodos, en `diseno` §5. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Hay tres catálogos, y cada valor pertenece a un grupo de sinónimos

**Decisión.** **`systems`** es el sistema de juego que usa la mesa (D&D, 20roll…); **`tags`**, las etiquetas para filtrar en la pantalla de inicio (campaña corta, pública…); **`platforms`**, dónde se juega (Roll20…). La relación entre valores de un mismo catálogo **no es una taxonomía: es un grupo de sinónimos**. Uno es el término canónico —el «principal» en pantalla— y los demás son formas alternativas de escribir lo mismo.

**Por qué.** La documentación vieja hablaba de «jerarquía de catálogos» con «navegación de árbol», y eso llevaba a implementar lo que no es. El propósito real es que «D&D», «Dungeon&Dragons» y «DANDD» sean **el mismo** tag: quien busca escribe el que conoce y encuentra las mesas etiquetadas con cualquiera de los otros. El CTE recursivo que quedó comentado en el legacy —subía de un tag a su raíz para resolver el canónico— confirma la intención; nunca se conectó a un endpoint.

*Antes: #53.*

## §2 La equivalencia es simétrica, así que el grupo es plano: cada alias apunta al canónico

**Decisión.** Buscar cualquier miembro del grupo trae todas las mesas etiquetadas con cualquier otro miembro; no hay especialización («el padre trae todo y el hijo solo lo suyo»). El modelo es **`canonical_id` plano**: cada alias apunta directo al canónico, nunca a otro alias, con **profundidad 1 siempre**, sostenida por `AbstractCatalogService` en cada operación —MySQL no puede expresar un `CHECK` que mire otra fila—. `V1__baseline.sql` nace con `canonical_id` plano; no hubo un `parent_id` que aplanar. Si algún día hace falta una taxonomía real (`D&D 5e` dentro de `D&D`), es una relación distinta y va en **otra** columna.

**Por qué.** En una relación simétrica la profundidad no significa nada: el CTE heredado subía nodo por nodo y descartaba todo salvo la raíz, así que la profundidad se calculaba y se tiraba en cada búsqueda. Con `canonical_id` ese resultado ya está guardado: la búsqueda deja de necesitar recursión, cada operación del admin es uno o dos `UPDATE`, y los ciclos (`A → B → A`), que el árbol permitía y podían colgar el CTE, se vuelven imposibles. Se reconsideró una taxonomía real al rediseñar la pantalla (§7) y se descartó: lo que se quería era ver el modelo que ya existe.

*Antes: #54, #59.*

## §3 Masters y admins proponen; solo un admin acepta y clasifica, y eso existe desde F1

**Decisión.** Masters y admins pueden proponer un valor nuevo; **solo un admin lo acepta y lo clasifica**: decide si es un canónico nuevo o un alias de un grupo existente. Mientras el valor está en `Created`, la mesa se publica igual y conserva el tag, pero **el tag no lo ven los jugadores ni filtra**; al aceptarse se muestra y filtra, y si se rechaza no aparece ni filtra nunca. La interfaz le deja claro al master que su tag está pendiente. La administración completa de catálogos (`/admin/catalogs`) se construyó en F1, junto con la propuesta, y no en F3.

**Por qué.** Es otra instancia del patrón de pedido con aprobación (ADR-16 §1). La clasificación tiene que ser del admin: si cada master decidiera a qué grupo pertenece su tag, los grupos se fragmentan y la equivalencia pierde su propósito; por eso fusionar y separar grupos son parte del producto, no un caso borde. El estado `Created` desacopla el ritmo del master del ritmo del admin: proponer no bloquea publicar, y un valor sin revisar no ensucia la búsqueda de los jugadores. Y como en `Created` el valor no sirve para nada, proponer sin que exista aceptar dejaba a F1 con media capacidad: es el criterio que ordenó las fases (ADR-01 §3), la fase arrastra lo que su actor necesita.

*Antes: #55, #57, #179.*

## §4 Se guarda y se muestra el alias que eligió el master; el grupo solo opera al buscar

**Decisión.** Al etiquetar se guarda **el alias tal como lo eligió el master**, y la mesa **siempre muestra ese alias**, aunque no sea el canónico. La búsqueda resuelve el grupo: buscar por un término encuentra las mesas etiquetadas con ese término, con su canónico y con todos sus equivalentes.

**Por qué.** La forma en que el master escribió el tag es información real, y reescribirla al canónico en pantalla la borraría. Y como la búsqueda resuelve por grupo y no por valor literal, **fusionar sinónimos después no obliga a reescribir `table_tags`**: la mesa etiquetada con «DANDD» se vuelve encontrable por «D&D» en el momento en que un admin une los dos, sin migrar una fila.

*Antes: #56, #58.*

## §5 Un valor se da de baja aunque esté en uso, y dar de baja un canónico exige elegir sucesor

**Decisión.** La baja es **lógica y sin restricciones**: un valor se puede dar de baja aunque esté en uso. Deja de mostrarse y de filtrar —igual que un rechazado (§3)—, `table_tags` conserva su fila, y restaurarlo devuelve todo como estaba. `disable` acepta un **`newCanonicalId`, obligatorio solo cuando el valor es canónico y todavía tiene alias vivos**: el sucesor pasa a canónico, el resto le re-apunta, y el valor dado de baja **queda en el grupo como alias del sucesor**.

**Por qué.** Prohibir el borrado de valores en uso rompía vínculos o dejaba callejones sin salida; la baja lógica no rompe nada y es coherente con el borrado lógico general (ADR-03 §7). Con `canonical_id` plano, dar de baja el canónico y cambiar el canónico son la misma operación, y el sucesor lo elige el admin: promover «el primer alias» sería una decisión que el sistema no tiene derecho a tomar. Que el valor dado de baja siga en el grupo es lo que mantiene encontrables las mesas etiquetadas con él.

*Antes: #81, #183.*

## §6 El admin trabaja un grupo con ocho operaciones, el alta directa y una lectura del grupo entero

**Decisión.** Las operaciones son **aceptar, rechazar, fusionar (`merge`), separar (`split`), mover un equivalente (`reassign`), hacer principal (`promote`), dar de baja (`disable`) y restaurar (`restore`)**, escritas una sola vez en `AbstractCatalogService` y cada una con su `@PreAuthorize("hasAnyRole('ADMIN','OWNER')")` en el método. `POST /admin/catalogs/{type}/{id}/reassign {canonicalId}` mueve **un alias** a otro grupo en una transacción; el destino es un canónico `Accepted` distinto del actual, y un canónico no se mueve solo: arrastra a su grupo, y eso es `merge`. `POST /admin/catalogs/{type}/{id}/promote` hace de un alias `Accepted` el principal de su propio grupo: el canónico anterior y los demás alias pasan a apuntarle, nadie sale del grupo, nada sale de circulación y ninguna mesa se toca. `GET /admin/catalogs/{type}/{id}/group` devuelve el grupo completo, canónico primero, con todos sus miembros cualquiera sea su estado, **sin paginar**.

**Por qué.** Mover era un `split` seguido de un `merge`: dos requests, y un fallo entre ambas dejaba al alias suelto como grupo propio. Cambiar el principal solo existía como efecto de dar de baja al que estaba, que además lo sacaba de circulación. Las dos sostienen la profundidad 1 en cada paso. La lectura del grupo existe porque fusionar necesita ver los grupos y dar de baja necesita los candidatos a sucesor, y nada de eso sale de una fila suelta del listado; no se pagina porque un grupo está acotado por cuántos sinónimos escribe una comunidad para una cosa.

*Antes: #184, #276.*

## §7 `/admin/catalogs` lista un grupo por fila, guarda su estado en la URL y abre cada grupo como un lienzo de nodos

**Decisión.** `/admin/catalogs` lista **un grupo por fila** (`?groupsOnly=true`): el canónico, o una propuesta sin clasificar —un grupo de uno—, con cuántos equivalentes tiene (`aliasCount`). Buscar encuentra un grupo por el nombre de **cualquiera** de sus miembros (una subquery `EXISTS`, para que la paginación siga en SQL). Catálogo activo, búsqueda y página son **query params**, no estado en memoria; las tres pestañas no son rutas hijas. Desde la fila se aprueba una propuesta como grupo nuevo o se rechaza, y el nombre abre **`/admin/catalogs/:kind/:id`**: el grupo como estrella —el principal al centro, con borde de marca, fondo `raised` y una corona con la palabra «Principal», los equivalentes alrededor con aristas rectas de centro a centro— y **las propuestas `Created` flotando a la izquierda con borde punteado** hasta que se las conecta. Conectar un valor al principal es clasificarlo: una propuesta se acepta, un equivalente se muda (`reassign`) y un principal fusiona su grupo, previa confirmación; quitar una arista lo separa. Una función pura (`connectionIntent`) decide qué significa cada conexión e `isValidConnection` no deja dibujar lo que el servidor rechazaría. Nada se pinta por adelantado: la mutación corre y el lienzo se redibuja desde el servidor. Otros grupos se traen del panel lateral y quedan en `?with=`. **Todo gesto tiene su entrada en el menú «…» del nodo**, y debajo de `md` el lienzo es una lista con ese menú. La librería es **React Flow (`@xyflow/react` 12.12.0, MIT)**, cargada con `lazy`, **sin motor de layout** (`radialLayout` ubica la estrella). Las piezas genéricas viven en `components/graph/` y el patrón queda en `diseno` §5 como la otra forma de trabajar una lista: cuando lo que se edita son relaciones, no datos.

**Por qué.** Una fila es algo que un admin le pasa a otro, y un estado en memoria no se enlaza; query params dan URL propia, compartible y botón de atrás sin inventar rutas que el sitemap no tiene. La tabla anterior mezclaba principales y equivalentes y cada movimiento era un diálogo que nombraba por texto dos cosas que no se veían juntas; el usuario pidió verlo por nodos, «como los flows de n8n», y usar algo hecho. React Flow y no Cytoscape, D3 o un canvas propio porque es el estándar en React para *editar* grafos con conexiones y su UI se construye sobre shadcn y Tailwind 4; sin dagre ni ELK porque resuelven grafos por capas de cualquier profundidad y acá hay una. La corona hace legible quién encabeza sin depender solo del borde, y la arista recta evita que un miembro a la derecha de su cabeza la cruce.

*Antes: #185, #275.*

## §8 En el explorador, los comandos de catálogo son texto libre y el backend resuelve el grupo

**Decisión.** `/table_system`, `/table_tag` y `/table_platform` se declaran con `examples` —como `/file_name`—, no con `values` como `/file_type`. Lo escrito viaja tal cual y `GameTableSearchSpecification` lo expande al grupo de sinónimos (§2, §4) antes de tocar la tabla puente. Los ejemplos salen de `V3__catalog_seed.sql`, así que el primero que alguien pruebe devuelve mesas. `SearchField` no gana un tercer modo.

**Por qué.** `values` es para opciones finitas y ajenas al vocabulario de la gente —nadie tipea un MIME type— (ADR-22 §2), y los catálogos son lo contrario: son cientos, crecen cuando un master propone, y sus valores son las palabras que la gente usa. Ofrecer la lista obligaría al combobox a consultar el catálogo mientras se tipea, un buscador con debounce dentro del buscador que ADR-22 §1 unificó para que hubiera uno solo. Y el grupo de sinónimos ya perdona escribir mal: `DANDD` encuentra lo mismo que `D&D`. Si el uso demuestra que hace falta la lista, el tercer modo se agrega entonces (`arquitectura` §2.4: se abstrae después de ver la repetición).

*Antes: #246.*

## §9 Un valor que crea un admin nace aceptado y canónico

**Decisión.** Cuando un admin crea un valor desde `/admin/catalogs` no propone: el valor nace `Accepted` y canónico, como grupo propio (`AdminCatalogController` → `AbstractCatalogService.create`), y la cabecera de la pantalla lo ofrece para el catálogo de la pestaña activa. Lo que proponen los masters sigue el camino de §3.

**Por qué.** Quien aceptaría la propuesta es él mismo: hacerlo pasar por la revisión sería un paso vacío, y dejaría en la bandeja un ítem que solo él puede resolver.

*Antes: #280.*
