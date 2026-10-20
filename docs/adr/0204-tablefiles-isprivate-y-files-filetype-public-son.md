---
adr: 204
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0204: tablefiles.isprivate y files.filetype = 'Public' son ejes distintos

## Decisión

**`is_private` es del vínculo; `Public` es del archivo**

## Por qué

Confundirlos era el error más probable de F1.4. `is_private` responde «en *esta* mesa, ¿lo ven los jugadores o solo quien la dirige?» — el mismo archivo puede estar compartido en una mesa y privado en otra. `Public` responde «un admin lo publicó para toda la plataforma, con su audiencia (#64)» y no dice nada de ninguna mesa en particular
