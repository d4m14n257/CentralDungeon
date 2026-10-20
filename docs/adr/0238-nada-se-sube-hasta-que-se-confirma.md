---
adr: 238
estado: Aceptada
fecha: 2026-09-12
---

# ADR-0238: Nada se sube hasta que se confirma la operación completa.

> Elegir un archivo lo deja en el navegador; el botón que crea la mesa, envía la entrega o publica la petición es el que sube

## Decisión

`StagedFile` en `types/file.ts` —`new` con los bytes, `existing` con el id de lo reusado o publicado— y `useCommitStagedFiles`, que sube y devuelve `{fileIds, failed, reused}`. La lista de lo que está por subirse vive bajo la zona de subida, porque sin subida inmediata es la única señal de que el archivo se tomó

## Por qué

Subía en el momento de elegirlo, así que abandonar el wizard en el paso 2 dejaba el archivo en el servidor igual, y una subida interrumpida dejaba cosas que nadie pidió conservar. El wizard **ya difería el vínculo** —guardaba ids y adjuntaba al crear la mesa (#228)— así que lo único que faltaba era diferir también la subida. **Un fallo no deshace el resto**: la mesa se crea, la entrega se manda, y se vincula lo que sí subió, porque perder cuatro pasos de trabajo por un archivo malo es peor que el archivo faltante — y todo lo que pasa por acá se puede completar editando después (la mesa es editable en preparación, la petición es del master, y las entregas se acumulan #76). Se avisa el nombre de lo que falló, y **reintentar sale gratis** porque el servidor reconoce el contenido que ya tiene (#75). **Obliga a validar en el cliente**: el tope y el whitelist los contestaba el servidor al subir, y diferida esa respuesta llegaría después de llenar cuatro pasos — así que `rejectionOf` los comprueba al elegir, devolviendo **el mismo código de error** que usaría el backend para que la frase sea una sola (#197). El servidor sigue siendo la autoridad. **Queda pendiente** el estado de revisión antes de enviar para las postulaciones a mesa, que no se editan una vez enviadas: es F2, junto con `registration_files`.
