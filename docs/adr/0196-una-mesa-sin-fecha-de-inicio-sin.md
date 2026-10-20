---
adr: 196
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0196: Una mesa sin fecha de inicio, sin agenda o sin sesiones previstas se abre con cero sesiones

## Decisión

`TableSessionService.materialize` no encuentra con qué trabajar y no hace nada. La aprobación del admin sigue su curso

## Por qué

Las tres columnas son nulables desde el baseline, así que exigirlas para aprobar sería una compuerta nueva sobre el flujo del admin, decidida de costado al implementar otra cosa. Materializar es **consecuencia** de abrir, no requisito para abrir: el master completa lo que falta y las sesiones aparecen. Si más adelante se decide que una mesa no puede abrir sin calendario, es una validación de `CreateGameTableRequest` y no un efecto silencioso de `approve()`
