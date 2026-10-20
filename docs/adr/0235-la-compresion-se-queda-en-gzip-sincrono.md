---
adr: 235
estado: Aceptada
fecha: 2026-09-10
---

# ADR-0235: La compresión se queda en gzip síncrono

> Se descartan zstd y hacerla asíncrona

## Decisión

Sin cambios en `LocalDiskStorageService`. Queda la medición para no reabrir la pregunta.

## Por qué

Medido con `gzip` y `zstd` reales sobre el whitelist: PDF **6.7 % / 7.0 % / 7.8 %** (gzip -6 / zstd -3 / zstd -19), PNG 6.8–17.2 % contra 5.5–17.5 %, JPEG ~1 % con los tres. **zstd le gana a gzip por menos de un punto porcentual**, porque PDF, PNG, JPEG y DOCX ya vienen comprimidos — el único formato del whitelist que comprime de verdad es el `.doc` viejo. Su ventaja real es la velocidad (5,5 ms contra 22 ms sobre 1,2 MB) y es irrelevante con un tope de 2 MB. No justifica una dependencia JNI con binarios nativos por plataforma. **Asíncrona tampoco**: obligaría a un estado `Uploading`/`Ready` y a que un archivo recién subido no se pueda descargar, a cambio de ahorrar 22 ms en una petición que ya cruzó la red. Lo difícil ya estaba resuelto — el staging con el move en `beforeCommit` (M26.2) es lo que impide los huérfanos. **Corolario incómodo y honesto: la compresión no es un lever de #75 para este whitelist.** Los que sí lo son son la reutilización y la purga.
