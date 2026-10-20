---
adr: 15
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0015: Almacenamiento de archivos

## Decisión

**Disco local, detrás de `StorageService`**

## Por qué

Se conserva el comportamiento actual, pero aislado tras una interfaz para poder pasar a S3 después sin tocar el resto del backend.
