---
adr: 237
estado: Aceptada
fecha: 2026-09-10
modificada-por: [278]
---

# ADR-0237: La biblioteca personal y la de la plataforma son dos vistas distintas

> , y los cajones que alguien puede usar en la suya salen de sus roles

## Decisión

`/my/files` es lo personal: **cada lado sigue al rol que lo llena**: `PlayerApplication` y `PlayerSubmission` a quien tenga `Player`, y `TableMaterial` y `MasterRequest` a quien tenga `Master` **o dirija alguna mesa** (#135). En producción casi todos ven los cuatro porque toda cuenta nace `Player` (#38) y un master acumula — pero «casi todos» no es «todos», y a una cuenta que no tiene `Player` hay que decirle la verdad en vez de ofrecerle cajones que sus flujos no pueden llenar. La lista puede volver **vacía**, y es una respuesta y no una falla. **`Announcement` no aparece nunca**, para nadie. `/admin/files` es la de la plataforma, y **gana su propia caja de subida**. **Corregido (#278)**: esa caja pide los cajones antes y publica al subir, y la lista es solo lo publicado — los archivos privados de las personas ya no aparecen. `FileService.personalCategoriesOf` decide y `GET /files/mine/categories` se lo dice a la pantalla

## Por qué

Estaban mezcladas, y la mezcla producía dos cosas raras. Una: `/my/files` ofrecía los cinco cajones a todo el mundo, así que un jugador podía archivar algo como material de mesa y un anuncio de la comunidad podía vivir en la biblioteca de una persona — cuando un anuncio es público por definición y solo existe en la de la plataforma. Otra, al revés: **un admin no tenía dónde subir**. Para publicar algo había que adjuntarlo antes a una mesa y publicarlo desde ahí, así que el archivo llegaba arrastrando un cajón que le tocó de rebote; el propio e2e lo decía en un comentario. Con las dos vistas separadas cada una hace lo suyo: el admin sube en la de la plataforma y dice al publicar para qué flujos es —que es el acto deliberado—, y su rol no agrega ni saca nada de su biblioteca personal, porque ahí no va nada de su trabajo de admin. **El lado master sigue a la pertenencia y no solo al rol**: alguien a quien un admin asignó a una mesa no tiene el rol `Master` y la dirige igual (#135). **Y la regla vive en el service**: la pantalla ofrece lo que el servidor acepta, pero una petición que nombra otro cajón se rechaza (#121). **Ampliado (#241)**: una cuenta que no es `Player` ni `Master` no tiene biblioteca ninguna, así que `/my/files` no se le ofrece en el menú y se lo dice si entra por URL.
