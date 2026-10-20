---
adr: 51
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0051: Moderación de comentarios

## Decisión

**Todos los comentarios confirmados pasan por un admin**, que lee el contenido y el destinatario —nunca el autor— y decide si pasa o no. **El karma se mueve solo con los aprobados**; un `Rejected` no llega a la persona ni la afecta

## Por qué

Cierra **M17.3**. Se evaluó moderar solo los reportados y se optó por moderar todo: el karma es una métrica de comportamiento con efecto real sobre quién entra a una mesa, y un comentario injusto o innecesario que ya movió puntos es difícil de deshacer. Los estados del schema heredado (`Under review` / `Reviewed` / `Rejected` / `Deleted`) sirven tal cual. **Implicación operativa**: el karma se mueve al ritmo en que los admins moderan, así que la bandeja de moderación es parte del producto, no un extra.
