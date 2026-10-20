---
adr: 269
estado: Aceptada
fecha: 2026-09-28
modifica: [103]
---

# ADR-0269: Cada contexto se cierra a quien no lo tiene, y la respuesta es volver a casa, no un 403

> Corrige #103 (la parte de «los layouts no chequean roles»), #222 («`/player` no exige `Player`») y ajusta #241

## Decisión

Cada layout de contexto monta `RequireContext`: **`/player/*` exige el rol `Player`; `/master/*`, el rol `Master` o una fila viva en `masters` (#135); `/admin/*`, `Admin` u `Owner`**. Quien no tiene el contexto no ve ni el header de ese contexto ni su nav: se lo manda a `/`, que lo despacha a la home del que sí tiene (#222). `/master/tables/new`, que pide el rol `Master` y no solo pertenencia, redirige a `/master` en vez de pintar `ForbiddenState`. La regla es **la misma** que calcula `useAvailableContexts` para el chip, así el chip y la puerta no pueden discrepar. Las transversales (`/notifications`, `/my/*`, `/help`) no son de ningún contexto y no cambian. **Sin ningún contexto** (todos los roles revocados), `RootRedirect` explica en pantalla en vez de navegar, porque `/` → `/player` → `/` sería un bucle. `ForbiddenState` **sigue existiendo** para el otro rechazo: un recurso concreto, dentro de un contexto que el lector sí tiene, que no es suyo (#121)

## Por qué

Lo encontró el usuario en la revisión mano a mano de F4: `/admin` a secas pintaba el layout con un `Outlet` vacío a cualquiera con sesión, y **cualquier rol podía abrir las pantallas de los demás** —el backend respondía 403 y no devolvía datos, pero la vista se veía—. El usuario decidió que el cliente no debe ni llegar a ver una vista que no le corresponde, y tampoco leer un «sin permiso»: forzar una puerta ajena se contesta devolviéndote a tu zona. **#103 no se cae entero**: el contexto sigue sin ser seguridad y el backend autoriza endpoint por endpoint igual que antes (#121, #122); lo que cambia es que la interfaz deja de mostrar lo que no es tuyo. Tampoco se cae #135: la puerta del contexto Master es la misma que la del chip, así que un co-master asignado sin el rol sigue entrando a su mesa. **Consecuencia anotada, no resuelta acá**: dos cruces de contexto de `fase-4-revision.md` §2.2 se vuelven redirecciones — el `UserMenu` manda a `/player/profile` a una cuenta sin `Player`, y las pestañas del master enlazan a `/player/users/:id` — y se triajan en F4.1
