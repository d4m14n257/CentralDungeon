---
adr: 52
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0052: Borrado de borradores

## Decisión

**`deleted_at` acompañado de purga: se vacían `author_id` y el texto.** Aplica tanto al borrador que expira sin confirmarse como al de una mesa cancelada

## Por qué

Cierra **M18**. Es la única excepción a la convención de borrado lógico (#25), y está justificada: una fila marcada como borrada sigue siendo una fila legible, y un borrador expirado completo es exactamente el registro de "quién iba a decir qué sobre quién" que #43 prohíbe. Vaciarlo conserva el dato agregado que sí sirve —cuánta gente escribe y no confirma— sin guardar nada identificable. Nota: la fila sobrevive en backups y binlog el tiempo que estén configurados para retener; lo que se garantiza es que la base viva no lo tenga.
