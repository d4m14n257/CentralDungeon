---
adr: 37
estado: Aceptada
fecha: 2026-08-29
modificada-por: [89]
---

# ADR-0037: Modelo de roles

## Decisión

**Los roles son funciones acumulables, no niveles. No hay jerarquía ni herencia de permisos**

## Por qué

Un usuario puede ser `Master` y `Player` a la vez (crea y administra sus mesas, y juega en las de otros), y puede ser `Master` sin ser `Player`. **Ampliado por #67: los roles son cuatro, no tres — falta `Owner`.** `Admin` **no** hereda los permisos de `Master`, ni `Master` los de `Player`: cada rol habilita su propio conjunto de funciones. En consecuencia **no se configura un `RoleHierarchy` en Spring Security** y cada endpoint enumera explícitamente los roles que lo alcanzan (`hasAnyRole('MASTER','ADMIN')`), nunca el "mínimo" de una escala que no existe.
