---
adr: 234
estado: Aceptada
fecha: 2026-09-10
---

# ADR-0234: Una subida deduplicada responde 200, no 201

## Decisión

`FileService.upload` devuelve `UploadResult(file, deduplicated)` y el controller elige el código. El frontend lee el status con `api.uploadWithStatus` y el `FileDropzone` avisa «ya lo tenías subido».

## Por qué

Respondía 201 con la fila vieja, indistinguible de una escritura nueva, así que **la reutilización —el lever más barato de #75— pasaba en silencio absoluto** y la única persona que podía aprender de ella no se enteraba nunca. 200 vs 201 es exactamente lo que esos códigos significan y no necesita un campo en el DTO: el flag es un hecho sobre *esta petición*, no sobre el archivo, y ponerlo en `FileResponse` obligaría a ignorarlo a todos los demás lectores de esa forma. **La fila reconocida no se reclasifica**: volver a subir los mismos bytes con otra categoría es alguien clasificando, no corrigiendo, y la fila puede estar adjunta a mesas que la muestran. Corregirla es `PATCH /files/{id}`, sobre el archivo que la persona está viendo.
