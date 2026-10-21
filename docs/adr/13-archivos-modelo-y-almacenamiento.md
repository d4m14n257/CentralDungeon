---
adr: 13
titulo: "Archivos: modelo y almacenamiento"
estado: Vigente
actualizada: 2026-10-20
absorbe: [4, 15, 60, 61, 64, 65, 66, 68, 75, 80, 199, 200, 201, 202, 203, 204, 234, 235, 238]
---

# ADR-13: Archivos: modelo y almacenamiento

> Qué es un archivo en el sistema, de quién es, cómo se guarda en disco y cómo se contiene su costo. Cómo se leen, se clasifican en cajones y se publican está en ADR-14 §4 y su tema. El modelo de `files` está en `arquitectura` §4. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 `files` es el gestor de archivos de todo el sistema, y los personajes son archivos

**Decisión.** `files` sirve a cuatro usos: (1) los requisitos de la mesa, que sube su master; (2) la respuesta del jugador al postularse, incluida su hoja de personaje; (3) los archivos públicos de la plataforma (hojas por defecto, reglas, comunicados); (4) los archivos propios de cada usuario. Los personajes de jugador **siguen siendo un archivo adjunto genérico**; estructurarlos (nombre, clase, nivel, stats) queda como candidato futuro, fuera de la v1.

**Por qué.** El schema heredado no dejaba ver el alcance real: `files`, `table_files` y `registration_files` cubrían solo los usos 1 y 2, y a medias; los usos 3 y 4 existían en el código (`type_file` con `Public`, `Private` y `Single-use`) pero esa columna —igual que `files.size`— nunca estuvo en `database.sql`, un DDL que se admitía desactualizado en su primera línea. Estructurar los personajes era ampliar el alcance de la v1 sin necesidad.

*Antes: #4, #60.*

## §2 El tipo de archivo es su ciclo de vida, y es un eje distinto de la privacidad del vínculo

**Decisión.** Un archivo es de quien lo subió y tiene un `file_type` según su ciclo de vida: **`Single-use`** (transitorio, subido para un contexto concreto), **`Private`** (lo que el usuario eligió guardar, reutilizable mientras quiera) y los de la biblioteca de la plataforma, que no pertenecen a nadie en particular: **`Public`** (publicado) y **`Library`** (en la biblioteca sin publicar, ADR-14 §7). Promover un `Single-use` a `Private` es la acción de «guardar esto para después». El usuario accede a todo lo que subió en cualquier momento y puede volver a vincularlo a cuantos contextos haga falta: lo que cambia es el vínculo, no el archivo. Los públicos **no llevan audiencia**: a quién le sirven lo dice el cajón (ADR-14 §4). **`table_files.is_private` es del vínculo y `Public` es del archivo**: el primero responde «en *esta* mesa, ¿lo ven los jugadores o solo quien la dirige?» —el mismo archivo puede estar compartido en una mesa y privado en otra—; el segundo, «un admin lo publicó para toda la plataforma», y no dice nada de ninguna mesa.

**Por qué.** Los tipos responden a ciclos de vida distintos, por eso se conservan: lo que se eligió guardar vive mientras su dueño quiera, lo subido para un contexto es transitorio, y lo publicado es de la comunidad. La reutilización es la principal palanca de costo: la hoja de personaje que un jugador ya subió se adjunta de nuevo en vez de volver a almacenarse. Confundir `is_private` con `Public` era el error más probable al construir los archivos de mesa.

*Antes: #64, #65, #68, #204.*

## §3 No hay cuota por usuario: el costo se contiene reutilizando, purgando y con un tope por archivo

**Decisión.** Ningún usuario tiene cuota de almacenamiento. Las palancas son tres: **reutilizar** lo ya subido, mostrando el historial al momento de adjuntar (§2, §4); **purgar** lo que lleva **90 días** sin usarse (`app.storage.retention`, configurable), para lo que cada archivo registra `last_used_at`; y un **tope de tamaño por archivo**, 2 MB por defecto, editable entre 1 y 25 MB en la configuración del sistema (ADR-17 §3). La purga **marca, no borra** (ADR-03 §7): la biblioteca de la plataforma está exenta, un vínculo vivo mantiene vivo al archivo, y un archivo nunca usado cuenta desde el día en que llegó. Liberar los bytes es §8. La compresión también se aplica (§7), pero con el whitelist actual no es una palanca de costo.

**Por qué.** Una cuota limita a la persona sin reducir el total: 20 MB por usuario son 20 GB con mil jugadores, exactamente el costo que se quería evitar. Reutilizar evita duplicados y purgar libera lo que ya no le sirve a nadie, que es atacar el volumen real. El legacy no tenía ningún límite —`multer` sin `limits`—, así que el tope por archivo se conserva.

*Antes: #61, #75.*

## §4 Se deduplica por dueño, y una subida reconocida responde 200

**Decisión.** Si un usuario sube bytes que ya tiene, el servidor reconoce su archivo existente en vez de guardar otro: la deduplicación es **por dueño, no entre usuarios**. `FileService.upload` devuelve `UploadResult(file, deduplicated)` y el controller responde **200 si reconoció la fila y 201 si la creó**; el frontend lo avisa («ya lo tenías subido»). La fila reconocida **no se reclasifica** aunque la nueva subida traiga otra categoría: corregirla es `PATCH /files/{id}`.

**Por qué.** El alcance lo fija el schema: `uk_files_storage_key` es `UNIQUE` y `user_created_id` es `NOT NULL`, así que compartir bytes entre personas necesitaría una tabla de blobs con conteo de referencias, que es otra decisión de schema. Por dueño cubre el caso común: la misma hoja adjuntada a una segunda mesa no cuesta nada. Responder 201 con la fila vieja hacía que la reutilización —la palanca más barata de §3— pasara en silencio para la única persona que podía aprender de ella. 200 contra 201 es lo que esos códigos significan, y el flag es un hecho sobre *esta petición*, no sobre el archivo, así que no va en `FileResponse`. No se reclasifica porque volver a subir los mismos bytes es clasificar, no corregir, y la fila puede estar adjunta a mesas que la muestran.

*Antes: #201, #234.*

## §5 Subir y vincular son dos pasos, y el cliente sube recién cuando se confirma la operación

**Decisión.** En la API, subir y vincular son dos llamadas: `POST /files` y después el vínculo al contexto (p. ej. `POST /game-tables/{id}/files`). En el cliente, **elegir un archivo lo deja en el navegador**: el botón que crea la mesa, envía la entrega o publica la petición es el que sube (`StagedFile`: `new` con los bytes o `existing` con el id de lo reutilizado; `useCommitStagedFiles`). La lista de lo que está por subirse va bajo la zona de subida. **Un fallo no deshace el resto**: la operación se completa, se vincula lo que sí subió y se avisa el nombre de lo que falló. El cliente valida tope y whitelist al elegir (`rejectionOf`) con **el mismo código de error** que el backend (ADR-23 §3); el servidor sigue siendo la autoridad.

**Por qué.** Con dos llamadas, «subir uno nuevo» y «reutilizar uno que ya tengo» terminan en la misma segunda llamada; un endpoint que subiera y vinculara de una volvería lo directo subir y lo raro reutilizar, al revés de lo que pide §3. Subir al elegir dejaba en el servidor lo de un wizard abandonado o una subida interrumpida; el vínculo ya se difería (ADR-08 §1) y faltaba diferir la subida. Perder varios pasos de trabajo por un archivo malo es peor que el archivo faltante, y todo se puede completar editando después; reintentar sale gratis porque el servidor reconoce lo que ya tiene (§4). Diferida la subida, la respuesta del servidor llegaría después de llenar todo el formulario: por eso el cliente valida antes.

*Antes: #203, #238.*

## §6 Los archivos van a disco local detrás de `StorageService`, nombrados por su id

**Decisión.** Los bytes se guardan en disco local detrás de la interfaz `StorageService` (`LocalDiskStorageService`). En disco cada archivo **se nombra por su id, nunca por el nombre original**, que vive en una columna aparte como metadato para la descarga. La entidad se llama **`StoredFile`, no `File`**.

**Por qué.** Se conservó el disco local del legacy, pero aislado tras una interfaz para pasar a S3 sin tocar el resto del backend. El legacy concatenaba el nombre original sin sanitizar (`<timestamp>-<originalname>`), una vía directa de path traversal; con el id, lo que escribe el usuario deja de tocar el sistema de archivos. `File` choca con `java.io.File` justo en el paquete que trabaja con rutas, el mismo problema que llevó a `GameSystem` (ADR-03 §9).

*Antes: #15, #80, #199.*

## §7 La escritura pasa por staging y se confirma en `beforeCommit`, comprimida con gzip síncrono

**Decisión.** El archivo se escribe a un área de **staging** y se mueve a su lugar en **`beforeCommit`**; el rollback borra lo que haya quedado. La compresión es **gzip, síncrona, dentro de `LocalDiskStorageService`** e invisible para el resto: quien llama entrega y recibe los bytes originales. `files.size_bytes` guarda el tamaño **sin comprimir**, que es el que se muestra y contra el que se aplica el tope.

**Por qué.** Base y disco no comparten commit: escribir directo deja un huérfano cada vez que falla la transacción que iba a registrar el archivo. El movimiento va *antes* del commit a propósito: si el disco no puede recibirlo, la transacción se cae y la fila nunca se escribe; al revés, esa falla no se deshace y deja una fila apuntando a nada. Mostrar el tamaño comprimido sería un número que la persona no reconoce. Se midieron gzip y zstd sobre el whitelist: PDF 6,7 % / 7,0 % / 7,8 % (gzip -6 / zstd -3 / zstd -19), PNG 6,8–17,2 % contra 5,5–17,5 %, JPEG ~1 % con los tres. **zstd gana por menos de un punto** porque PDF, PNG, JPEG y DOCX ya vienen comprimidos (solo el `.doc` viejo comprime de verdad); su ventaja es velocidad (5,5 ms contra 22 ms sobre 1,2 MB), irrelevante con este tope, y no justifica una dependencia JNI con binarios nativos. Asíncrona tampoco: obligaría a un estado `Uploading`/`Ready` y a no poder descargar un archivo recién subido, por ahorrar 22 ms en una petición que ya cruzó la red.

*Antes: #200, #202, #235.*

## §8 Borrar los bytes es una operación del owner de la plataforma

**Decisión.** El borrado lógico de la aplicación nunca toca los bytes. El borrado físico lo hace **el owner de la plataforma desde un menú de administración**, y contempla borrar también del almacenamiento en la nube.

**Por qué.** Liberar espacio real es una operación de mantenimiento deliberada y con responsable, no el efecto secundario de que alguien apriete «eliminar» ni de la purga de §3, que solo marca y deja margen para notar un error antes de que sea irreversible. Este owner es el rol de plataforma, no el `master_type` `Primary` que encabeza una mesa (ADR-06 §1).

*Antes: #66.*
