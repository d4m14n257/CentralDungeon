---
adr: 103
estado: Aceptada
fecha: 2026-08-29
modificada-por: [145, 269]
---

# ADR-0103: Navegación del frontend

## Decisión

**Cambio de contexto explícito por rol**: un chip muestra los contextos que el usuario tiene habilitados (Jugador, Master, Admin, Owner) y cada uno tiene su propia navegación. Quien tiene un solo rol **ve el chip igual, pero sin caret ni menú** — no hay nada que elegir, pero sí algo que mostrar (matizado por #145). **Cada contexto se cierra a quien no lo tiene** y se lo devuelve a su home (matizado por #269)

## Por qué

Con roles acumulables y sin jerarquía (#37, #67, #89), un menú único que crece según los roles se vuelve largo y mezcla actividades que no tienen nada que ver: explorar mesas para jugar y moderar comentarios no se parecen en nada. Separar por contexto mantiene cada espacio enfocado. **El contexto es organización de UI, no seguridad**: el backend sigue autorizando endpoint por endpoint, y estar "en contexto Admin" no habilita absolutamente nada.
