---
adr: 233
estado: Aceptada
fecha: 2026-09-10
modificada-por: [278]
---

# ADR-0233: El cajón de un archivo es el flujo del que viene, no qué documento es

> Y es una **relación**, no una columna. Cinco: `TableMaterial`, `MasterRequest`, `PlayerApplication`, `PlayerSubmission`, `Announcement`

## Decisión

Tabla `file_categories` (`V9`), add-only. La escribe el servicio que hace el vínculo: adjuntar a una mesa, responder una petición, adjuntar un formulario a un pedido. El único lugar que **pregunta** es `/my/files`, que no tiene flujo que observar. Al publicar, el admin elige uno o varios — y no puede elegir los dos del lado jugador. **Precisado (#278)**: elige al subir, no después — en `/admin/files` subir es publicar. **Deroga `public_audience` (#64) y su corrección M24.1**

## Por qué

**El primer intento estuvo mal dos veces y las dos importan.** Mal sobre **quién sabe**: nadie al subir puede declarar honestamente «esto es una plantilla de ficha» —lo que un master pide puede ser un formulario en un `.doc`— así que se preguntaba algo que no se puede contestar, y con un default (`Other`) que se llevaba casi todo. Lo único que el sistema sabe con certeza es **desde dónde se subió**. Y mal sobre la **cardinalidad**, que es por qué es tabla y no columna: un archivo pertenece a todos los flujos en los que se usó —una ficha subida en una postulación y después entregada en una petición está en los dos— y la deduplicación (#75) vuelve eso el caso normal, no un borde, porque los mismos bytes son una sola fila por diseño. Una columna tendría que elegir uno y mentir sobre el otro. **La pertenencia no se revoca nunca**: despegar dice que el archivo ya no es de esa mesa, no que nunca fue su material. **Y es lo que hace que un publicado comparta cajón con uno tuyo**: la hoja en blanco del admin no está adjunta a nada, así que no hay de dónde derivarle un cajón — está ahí *para* un flujo, no usada *en* uno. Una fila declarada lo dice, y el picker pide un cajón y recibe las plantillas de la comunidad y las tuyas en la misma forma, sin poder distinguirlas. **La audiencia se va porque el flujo ya dice a quién le sirve** y dos ejes decidiendo lo mismo terminan discrepando; los anuncios, que no salen de ningún flujo, pasan a ser su propio cajón. **Acotado (#242)**: la fila de toggles de cajón sigue en `/admin/files`, pero se fue de `/my/files` — ahí el cajón se narrowea con `/file_categories`, desde el buscador.
