---
adr: 48
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0048: Cuándo se confirma un comentario

## Decisión

**El comentario se escribe en cualquier momento de la mesa pero queda en borrador. Al llegar la mesa a `Finished`, el autor lo revisa —lo deja como está o lo corrige— y recién ahí queda confirmado y cuenta para el karma. Si la mesa se cancela, el borrador nunca se valida: no entra al karma ni se evalúa**

## Por qué

Cierra **M16** sin sacrificar nada. Resuelve las tres tensiones a la vez: se puede dejar la impresión en caliente sin perderla, se respeta que el mejor momento para evaluar es al final (#40) porque la versión que cuenta es la revisada al cierre, y una mesa cancelada no juzga a nadie (#46) sin necesidad de anular nada ni de revertir karma — el borrador simplemente nunca llegó a ser un comentario. También elimina la asimetría entre quien comentó temprano y quien esperó.
