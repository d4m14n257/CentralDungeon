---
adr: 199
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0199: Nombre de la entidad de archivos

## Decisión

**`StoredFile`, no `File`**

## Por qué

Mismo problema y mismo precedente que **#182** (`GameSystem` en vez de `System`): el nombre corto choca con un tipo del JDK —`java.io.File`— dentro del paquete que más lo necesita, porque la capa de almacenamiento trabaja con rutas justo abajo. Un `@Entity File` obligaría a importar `java.io.File` con nombre completo en cada archivo de la feature, o peor, a no darse cuenta de cuál de los dos se está usando
