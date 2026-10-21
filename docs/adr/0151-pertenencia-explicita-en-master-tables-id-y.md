---
adr: 151
estado: Aceptada
fecha: 2026-08-31
modificada-por: [152]
---

# ADR-0151: Pertenencia explícita en /master/tables/:id, y el logo vuelve al home del contexto activo

## Decisión

**Dos cosas del mismo hallazgo.** (a) `MasterTableDetailPage` ahora calcula `table.masters.some(m => m.userId === me?.id)` y muestra `ForbiddenState` para la pantalla **entera** —ni el encabezado— si el actor no es master de esa mesa puntual. Antes solo la pestaña de candidatos (protegida en el service) fallaba; el encabezado usaba el mismo `GET /api/v1/game-tables/{id}` público que consume `/tables/:id`, y se veía igual estuvieras o no habilitado a gestionar esa mesa. (b) El logo del header vuelve al home del **contexto activo** (`useContextStore`), no siempre a `/`: `/master/tables` en contexto Master, `/` en Jugador — la misma regla que ya aplicaba `ContextSwitcher` al cambiar de contexto, ahora sacada a un builder único (`masterTablesPath()`, `config/paths.ts`) que también reemplaza el string literal que `ContextSwitcher` tenía escrito a mano

## Por qué

(a) El usuario probó `/master/tables/:id` con una mesa ajena y vio el nombre, la descripción y el master real en la respuesta — no un dato privado (es el mismo `GameTableDetailResponse` que cualquier jugador ve en `/tables/:id` para decidir si postularse, y las acciones reales —candidatos, aceptar, rechazar, abrir— sí filtran por `masterService.isMasterOf()` en el service, confirmado leyendo `RegistrationService`/`GameTableService`, no asumido), pero tenía razón en el reclamo de fondo: la ruta de **gestión** no tiene por qué mostrar nada, ni el encabezado, si no correspondía pertenencia — mezclar "acá va el dato público" con "esta es la pantalla del master" en la misma URL es confuso más allá de si algo sensible se filtra o no. Se resolvió en el frontend, sin tocar el backend: el dato ya viaja con la lista de masters, alcanza con compararla contra el actor antes de pintar cualquier cosa. (b) Efecto colateral real que el mismo caso destapó: entrar a la mesa ajena y volver por el logo aterrizaba en `/` (el explorador de Jugador) aunque el contexto activo fuera Master — inconsistente con que el propio selector de contexto sí sabe navegar por contexto. **Lo que no se tocó, a propósito**: no existe `/player` ni se movió `/` — el el sitemap de `diseno` §2ya fija `/` como "Explorar mesas" de Jugador y reserva `/master` para el dashboard de Master, que es de **E2** (#136); la ruta "home" de Master en E1 es `/master/tables` porque es lo único que existe todavía. Verificado en vivo con `master-1` (que no es master de la mesa probada): pantalla entera reemplazada por "No tenés permiso", sin encabezado; y con el contexto puesto en Master, el logo llevó a `/master/tables`, no a `/`.
