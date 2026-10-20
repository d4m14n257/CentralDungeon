---
adr: 43
estado: Aceptada
fecha: 2026-08-29
modificada-por: [140]
---

# ADR-0043: Anonimato de los comentarios

## Decisión

**El autor de un comentario es anónimo siempre y sin excepción — tampoco lo ven los admins ni quien desarrolla el sistema. Lo único que puede saberse es la dirección del comentario (`JJ` / `JM` / `MJ`)**

## Por qué

Requisito duro del cliente, no negociable y no sujeto a "salvo para moderación". Es lo que hace que la gente comente con honestidad: si el autor puede ser identificado, un jugador no deja una reseña negativa sobre un master del que depende para entrar a futuras mesas, y el karma deja de medir comportamiento y pasa a medir prudencia política. **Consecuencia inmediata: `comments.user_created_id` como FK a `users` — tal como está en el schema heredado y en la propuesta v1 — incumple el requisito y no puede quedarse.** Detalle del rediseño en **M15**.
