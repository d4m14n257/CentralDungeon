---
adr: 76
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0076: Alcance del sistema frente a las entregas

## Decisión

**El sistema facilita los entregables, no juzga si se cumplieron.** Las entregas se acumulan, nunca se reemplazan entre sí, y no hay flujo de aprobación: revisarlas es responsabilidad del master. Además, el master puede **dirigir archivos a un jugador concreto**

## Por qué

Cierra las preguntas 3 y 4 del diseño de #63. Saber si un personaje entregado es "el definitivo" o si cumple lo pedido exige un criterio humano que el sistema no tiene y no debería fingir tener. Acumular en vez de reemplazar preserva el historial sin pedirle al sistema que decida cuál vale. El envío dirigido a una persona es el `audience = 'Single'` del diseño, y cubre el caso de asignarle a cada jugador su material.
