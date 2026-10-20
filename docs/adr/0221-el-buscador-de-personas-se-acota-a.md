---
adr: 221
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0221: El buscador de personas se acota a la mesa en vez de abrirse a más roles

## Decisión

**`GET /api/v1/game-tables/{id}/master-candidates`**, autorizado por pertenencia: el service verifica que el actor sea el `Primary` de **esa** mesa antes de responder. `GET /api/v1/users/search` **no se toca** y sigue siendo de admin

## Por qué

El Javadoc de `UserController.search` decía, desde #165, que ampliarlo cuando un master pudiera agregar co-masters tenía que ser *"una decisión explícita, no un efecto colateral"*. Llegó el momento y la decisión se tomó **al revés de lo esperado**: en vez de abrir el directorio a más roles, la búsqueda se cuelga de la mesa. Dos motivos. El primero es de alcance: `hasAnyRole('MASTER',…)` habría dejado el directorio de la comunidad accesible a cualquiera con el rol, tenga o no una mesa que administrar, mientras que acotado solo responde a quien efectivamente puede agregar un master. El segundo es que **un chequeo de rol no cubre el caso real**: un `Primary` que un admin asignó puede no tener nunca el rol `Master` (#72, #135) y habría fallado `hasRole('MASTER')` dirigiendo legítimamente la mesa — el mismo error que #135 existe para evitar. Encontrado por el e2e de F1.6, no por un unitario: el `UserPicker` mockeado en Vitest nunca llama a la API, así que el `403` solo aparece con el navegador y el backend reales, y en pantalla se veía como «Algo salió mal» debajo del buscador. `UserPicker` recibe un `tableId` opcional —un **id**, no una entidad (§3.1.5), así que sigue sin saber nada de mesas— y `queryKeys.users.search` pasa a llevar el alcance en la clave: el directorio y la búsqueda de una mesa son respuestas distintas a las mismas palabras y no pueden compartir entrada de caché.
