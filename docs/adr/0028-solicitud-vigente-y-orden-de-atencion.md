---
adr: 28
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0028: Solicitud vigente y orden de atención

## Decisión

**Como máximo una solicitud activa (`Candidate` o `Player`) por par mesa/usuario; la vigente es siempre la última. La lista de candidatos se atiende en orden de llegada (FIFO) por fecha de solicitud**

## Por qué

Cierra **M7**. Con el `UNIQUE` fuera (#23) hacía falta una regla para saber cuál de N filas manda: se resuelve acotando a una sola fila activa, así "la última" y "la activa" son la misma y no hace falta desempatar por fecha. El reintento solo se habilita cuando la anterior quedó en `Rejected`. El orden FIFO es una regla de equidad explícita del negocio: todos tienen la misma oportunidad de entrar, y el orden no se reordena por karma, antigüedad ni criterio del master.
