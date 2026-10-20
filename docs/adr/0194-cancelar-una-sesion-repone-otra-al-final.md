---
adr: 194
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0194: Cancelar una sesión repone otra al final del calendario

## Decisión

La sesión cancelada conserva su `sequence_number` como registro de que estaba planificada, y se agrega una nueva en la primera franja de la agenda posterior a la última sesión en pie, con el siguiente número. Una mesa sin agenda viva no repone nada

## Por qué

La mesa prometió `total_sessions` (#26) y una cancelación es una noche que no se jugó, no una campaña más corta. Se evaluó no reponer —lo más simple— y deja a la mesa jugando once de doce sin que nadie lo haya decidido; el master tendría que compensarlo a mano y no hay pantalla para eso en F1. Reponer al final mueve la fecha de cierre y nada más, que es lo que efectivamente pasa en una mesa real cuando se suspende una sesión. **La interfaz lo avisa antes de confirmar**, no después: enterarse al ver una sesión que no se esperaba sería una sorpresa, y el principio 3 de `frontend-diseno.md` §1 pide lo contrario
