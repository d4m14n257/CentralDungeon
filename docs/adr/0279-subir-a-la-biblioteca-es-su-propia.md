---
adr: 279
estado: Aceptada
fecha: 2026-09-30
modificada-por: [282]
modifica: [278]
---

# ADR-0279: Subir a la biblioteca es su propia página, /admin/files/upload, y cada archivo elige qué es en su fila de la lista, con un select

> Corrige la forma de #278, no su fondo

## Decisión

Se van el panel que se abría y cerraba con un `useState` sobre la lista y las casillas de cajones que #278 le había puesto arriba. La cabecera de `/admin/files` es un enlace a **`/admin/files/upload`** (`AdminFileUploadPage`): cabecera con «Volver a archivos», la zona de subida, **la misma `StagedFileList` de todos los flujos** con un **`PublishCategorySelect` por fila** (los tres cajones publicables, sin preselección), y al pie «Cancelar» y «Subir N archivos» (**#282** lo partió en «Subir sin publicar» y «Subir y publicar», los dos confirmados). `FileDropzone` gana `multiple`: se sueltan o eligen varios de una vez, cada uno entra a la lista por separado y un rechazo nombra su archivo. `StagedFileList` gana `renderControls` y, con controles, debajo de `sm` pone el nombre en su línea. **Sin elegir qué es un archivo, «Subir» no manda nada y dice cuántos faltan**, marcando los selects vacíos (`aria-invalid`); no se deshabilita en silencio (principio 2). Cada archivo sube con **un** cajón; el endpoint de #278 sigue aceptando varios y no cambió. Al terminar vuelve a `/admin/files`; lo que falló queda listado con su cajón

## Por qué

Lo corrigió el usuario apenas vio #278: se veía peor. El botón de enviar quedaba suelto, elegir el cajón **antes** y para todos a la vez no es cómo se piensa una subida —cada archivo es un documento distinto: la hoja en blanco es material de mesa, el reglamento es un anuncio—, y la lista de subida ya funcionaba bien: faltaba **un select para decir qué es cada uno** y poder subir varios, no rehacer la pantalla. Y un apartado exclusivo para subir es **una página**, con su URL y el botón «atrás» del navegador, no una sección que un estado prende sobre la lista. **Un select y no casillas** porque un archivo es una cosa: publicar la misma hoja en dos cajones sigue siendo posible volviéndola a subir con el otro, que no guarda otra copia (#75). `/my/files` tiene el mismo panel con `useState` y no se tocó: es otra pantalla y no entró en este pedido
