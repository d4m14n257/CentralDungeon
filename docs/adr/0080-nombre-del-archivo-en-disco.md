---
adr: 80
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0080: Nombre del archivo en disco

## Decisión

**Se nombra por el id del archivo, nunca por el nombre original. El nombre original vive en una columna aparte, como metadato para la descarga**

## Por qué

Cierra **M26.3**. El legacy concatenaba el nombre original sin sanitizar (`<timestamp>-<originalname>`), que es una vía directa de path traversal. Con el id como nombre físico, el nombre que sube el usuario deja de tocar el sistema de archivos y pasa a ser un dato más.
