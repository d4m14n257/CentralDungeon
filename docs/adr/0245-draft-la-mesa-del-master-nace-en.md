---
adr: 245
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0245: Draft: la mesa del master nace en borrador, y enviarla a revisión se la saca de las manos

## Decisión

Estado nuevo, el décimo. `create()` deja la mesa en `Draft` — solo la ve su master, no aparece en ningún listado ni le avisa a ningún admin — y un `POST /{id}/submit` la manda a `Preparation`. `EDITABLE_STATUSES` pasa a `{Draft, ChangesRequested}`: **`Preparation` deja de ser editable por el master**. `DELETABLE_STATUSES` suma `Draft`. La mesa que crea un **admin** no pasa por acá: nace `Unassigned` como hasta ahora (#72)

## Por qué

`Preparation` significaba dos cosas a la vez —«la estoy escribiendo» y «la estoy esperando»— y por eso el master podía reescribir una mesa mientras un admin la leía, que es como un revisor termina aprobando algo que ya no existe. Separarlas da la regla que faltaba: se edita cuando la mesa es tuya, y es tuya antes de mandarla o cuando te la devuelven. **Enviar es deliberado y no automático al crear**: una mesa se escribe en varias sentadas, y un wizard que la archivara para revisión apenas se guarda pondría borradores a medio escribir delante de un revisor. El admin no tiene borrador porque no tiene a quién esperar: revisar es su trabajo, así que sería un paso que termina donde empieza. En el frontend, las **tres** copias del literal `EDITABLE_STATUSES` —la página de edición, el detalle que pinta el botón y la pestaña de agenda— se unifican en `features/tables/lifecycle.ts`: eran tres lugares para actualizar dos.
