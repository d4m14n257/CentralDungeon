---
adr: 39
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0039: Quién puede vetar, y si se deshace

## Decisión

**El `Owner` es el único que veta oficialmente. Un co-master puede pedir el veto, pero necesita la aprobación del `Owner` y una justificación. El veto es reversible, y tanto el veto como su levantamiento quedan registrados**

## Por qué

Cierra **M12**. Le da contenido real a la distinción `Owner`/`Master`, que si no quedaba decorativa, y sostiene la jerarquía dentro de la mesa: el dueño de la mesa responde por quién está y quién no. La reversibilidad es realismo: en una partida de D&D la gente se altera y un veto en caliente es un escenario esperable, así que tiene que poder deshacerse — pero queda en el historial, para que un patrón de vetos impulsivos sea visible.
