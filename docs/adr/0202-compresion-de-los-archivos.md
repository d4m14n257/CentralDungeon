---
adr: 202
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0202: Compresión de los archivos

## Decisión

**gzip adentro de `LocalDiskStorageService`, invisible para el resto**

## Por qué

La compresión de #75 es del almacenamiento, no del dominio: quien llama entrega y recibe los bytes originales. `files.size_bytes` guarda el tamaño **sin comprimir**, que es el que se muestra y contra el que se aplica el tope — mostrar el comprimido sería mostrar un número que la persona no reconoce
