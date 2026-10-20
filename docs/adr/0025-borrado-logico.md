---
adr: 25
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0025: Borrado lógico

## Decisión

**Columna `deleted_at DATETIME NULL` explícita además del `status = 'Deleted'`, y cascada de borrado lógico resuelta en el service**

## Por qué

El `status` dice *que* está borrado pero no *cuándo*, y sin fecha no se puede purgar, auditar ni deshacer. La cascada se vuelve explícita: borrar una mesa marca como borradas sus solicitudes, su agenda, sus adjuntos y sus vínculos de catálogo, todas con la **misma** marca de tiempo y en una sola transacción. Sigue sin haber `ON DELETE CASCADE`: es lógica de negocio y vive en el service (regla dura 7).
