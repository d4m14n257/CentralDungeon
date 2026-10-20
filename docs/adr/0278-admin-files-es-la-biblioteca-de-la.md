---
adr: 278
estado: Aceptada
fecha: 2026-09-30
modificada-por: [279, 282]
modifica: [233, 237]
---

# ADR-0278: /admin/files es la biblioteca de la plataforma y nada más: solo lo publicado, y subir ahí es publicar, con los cajones elegidos antes

> Corrige #237 y precisa #233

## Decisión

**La forma de la pantalla la corrigió #279.** La subida de `/admin/files` pedía **primero** los cajones (`PublishCategoriesField`, los tres publicables como checkboxes, nada preseleccionado) y mandaba todo junto a **`POST /api/v1/admin/files`** (multipart: `file` + `data` con `PublishFileRequest`), que guarda y publica en la misma transacción (`FileService.uploadPublished`); el cajón del lado jugador se rechaza **antes** de guardar un byte. Se van `POST /admin/files/{id}/publish`, `PublishFileDialog` y `usePublishFile`: **no existe publicar un archivo ya subido**. **Corregido por #282**: subir ya no obliga a publicar, publicar vuelve como acción de la fila y despublicar deja el archivo *oculto* en la biblioteca en vez de devolvérselo a quien lo subió. La lista lleva `fileType = Public` en el `WHERE` —no es un filtro que la pantalla pueda soltar—, se va el parámetro `fileType`, y filtrar por `PlayerApplication` o `PlayerSubmission` es 400. Los toggles de cajón, el comando `/file_categories` y los badges de cada fila muestran **tres** cajones, no cinco: una membresía del lado jugador que un publicado arrastre de antes no se muestra, porque es su historia como respuesta de alguien, no un estante de la biblioteca. `deleteAsAdmin` solo alcanza publicados: un archivo privado de otro es **404**. Despublicar lo saca de la biblioteca —vuelve a ser `Private` de quien lo subió— y **volver a publicarlo es volver a subirlo**, que no cuesta nada porque la deduplicación (#75) devuelve la misma fila, ahora publicada. Se va la columna «Tipo»: todas las filas son `Public`

## Por qué

Lo encontró el usuario revisando F4 como admin, con dos preguntas. **¿Cómo se sube un archivo sin decir qué es?** #237 había dejado la subida sin cajón a propósito —«lo dice al publicar, que es el acto deliberado»—, y en la práctica eso dejaba cada archivo un rato en un estado que no significaba nada: `Private` del admin, sin cajón, **en su biblioteca personal**, que es justo donde #237 decía que no va nada del trabajo de admin. Si la lista de cajones existe, se elige al subir. **¿Por qué el filtro ofrece cinco cajones si el admin publica en tres?** Porque la pantalla mezclaba dos cosas: la biblioteca de la comunidad y «todo lo que subió cualquiera», así que las postulaciones y entregas de los jugadores aparecían en una pantalla de administración con sus cajones. Pensando el ciclo de vida: el master es quien pide y reparte las hojas; el jugador las baja, las llena y las sube en **su** postulación o **su** entrega. Ahí no hay nada que la comunidad publique, y una categoría que el admin nunca puede llenar no tiene qué hacer en su vista. **Se descartó una segunda pestaña de moderación** («Todos los archivos») y la decisión fue del usuario: ver o dar de baja el archivo privado de alguien —y avisarle— es lo que se hace **personificando** a esa persona, no desde la biblioteca; acá se controla lo general, no se borran cosas de jugadores. Se perdió con eso la forma de publicar un archivo que ya subió un master, y se acepta: si hace falta, el admin lo sube. El primer e2e de `table-files.spec.ts` verificaba «un archivo, dos mesas» mirando la fila en `/admin/files`, que ya no muestra archivos privados; pasó a verificarlo en `/my/files` del master, y a comprobar que el admin **no** lo ve
