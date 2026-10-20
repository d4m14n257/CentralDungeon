---
adr: 89
estado: Aceptada
fecha: 2026-08-29
modificada-por: [169]
modifica: [37, 67]
---

# ADR-0089: Owner incluye a Admin

## Decisión

**`Owner` es un admin con privilegios adicionales: todo lo que puede un `Admin`, lo puede un `Owner`. Ninguna otra relación de este tipo existe** — `Owner` no implica `Player` ni `Master`, y para jugar o dirigir hace falta tener ese rol

## Por qué

Precisa #37 y #67, que decían "ninguna jerarquía" sin excepciones. La excepción es una sola y va en una dirección. **No se implementa con un `RoleHierarchy` de Spring Security**: cada endpoint de administración escribe `hasAnyRole('ADMIN','OWNER')` explícitamente. Un bean de jerarquía haría que el permiso de un endpoint dependa de una configuración lejana, que es justo lo que #37 quiere evitar.
