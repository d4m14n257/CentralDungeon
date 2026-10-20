---
adr: 84
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0084: Salir del servidor de Discord

## Decisión

**Salirse no borra nada: los datos se conservan por si la persona vuelve. Ser baneado del servidor sí implica quedar baneado del sistema, sin retorno**

## Por qué

Distingue dos situaciones que el login trata igual (#38): quien se fue por su cuenta y quiere volver conserva karma, historial y mesas; quien fue expulsado de la comunidad no vuelve a entrar. Sin esto, salirse un mes del servidor equivaldría a perder dos años de historial. **Ver M30**: hoy el sistema no puede distinguir un caso del otro por sí solo.
