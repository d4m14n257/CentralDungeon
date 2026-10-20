---
adr: 68
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0068: Clasificación de archivos

## Decisión

**Se conservan los tres tipos: `Public` (los publica un admin, con su audiencia #64), `Private` (del usuario, reutilizable, cuenta contra la cuota) y `Single-use` (temporal, atado a un contexto y se purga cuando ese contexto termina)**

## Por qué

Corrige el alcance de #65, que había eliminado la distinción. Los tres tipos responden a ciclos de vida distintos y por eso conviene conservarlos: lo que el usuario **eligió guardar** vive mientras él quiera y ocupa su cuota; lo que subió para una mesa concreta es transitorio y se purga; lo que publica un admin no pertenece a nadie en particular. La reutilización de #65 sigue valiendo sobre los `Private`, y promover un `Single-use` a `Private` es la acción de "guardar esto para después" — que es exactamente lo que hacía `handlePrivateStatus` en el legacy. **Resuelve además M27**: la cuota aplica solo a los `Private`, así que guardar para reutilizar y limitar el costo dejan de estar en conflicto.
