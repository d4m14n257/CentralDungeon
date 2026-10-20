---
adr: 135
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0135: Master de una sola mesa, sin el rol de plataforma

## Decisión

**El rol `Master` y dirigir una mesa son dos permisos distintos y se autorizan distinto.** El rol de plataforma `Master` significa **"puedo crear mesas propias"**. Dirigir una mesa concreta se autoriza **solo por la fila en `masters`**, sin exigir el rol. Un `Player` al que un admin asigna como master de una mesa (#72) tiene ahí **todas** las atribuciones de master —candidatos, agenda, sesiones, peticiones, vetos, pausa— y ninguna fuera de ella. **Sin cambio de schema**: `masters` ya es `(game_table_id, user_id, master_type)`

## Por qué

Es un caso real que faltaba: los admins abren mesas y reclutan a un jugador para dirigir **esa** mesa, y al terminar no queda como master de nada. El modelo ya lo soportaba y nadie lo había notado — la señal estaba en #90, que dice "pedir el rol de master **de forma permanente**" sin que existiera jamás la variante no permanente. Lo que no lo soportaba era la autorización: con `hasRole('MASTER')` en los endpoints de mesa, ese jugador queda afuera pese a tener su fila en `masters`. Se descartó **conceder y revocar el rol de plataforma** al asignarlo y al cerrar la mesa: obliga a preguntar "¿le queda alguna otra mesa viva?" antes de revocar, mete escritura de roles en un flujo que no es de roles, y convierte un permiso de mesa en uno global durante toda la partida — si dirige una mesa, podría crear otras. La regla dura 17 ya decía lo correcto: **el rol no es la pertenencia**. Acá se aplica al revés de como se suele leer — no es que además del rol haga falta pertenencia, es que **la pertenencia sola alcanza** y el rol sobra. La caducidad sale gratis: al pasar a `Finished` la mesa deja de admitir mutaciones por su propia máquina de estados (#27), así que "hasta ahí llegó" no necesita ningún job ni columna. La fila en `masters` **no se borra**: es el registro histórico de quién dirigió esa mesa. **Dos consecuencias en la interfaz**: el `ContextSwitcher` muestra el contexto Master si hay rol **o** al menos una fila viva en `masters`; y `/master/tables/new` se ofrece **solo con el rol**, porque un master de una sola mesa no crea mesas.
