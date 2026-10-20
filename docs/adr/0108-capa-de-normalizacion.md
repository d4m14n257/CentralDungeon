---
adr: 108
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0108: Capa de normalización

## Decisión

**Se elimina.** No hay `normalize/models`: el tipo base de cada entidad (#20, skill `arquitectura` §3.2) es el contrato

## Por qué

La normalización existía para que un cambio de nombre en el backend no rompiera el frontend. Con TypeScript estricto eso es exactamente lo que **no** se quiere: si el backend renombra un campo, la compilación tiene que fallar. Una capa que traduce nombres **esconde** la ruptura y la convierte en un bug de runtime, que es el problema que el tipado vino a resolver. Si alguna vez la forma de la API y la de la UI difieren de verdad (una fecha ISO que se quiere como objeto), eso es un **mapeo** y vive en el `api/` de su feature, no en un normalizador global.
