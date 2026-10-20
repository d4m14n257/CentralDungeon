---
adr: 169
estado: Aceptada
fecha: 2026-09-02
modifica: [89]
---

# ADR-0169: Admin y Owner no se acumulan: son el mismo rol con distinto alcance

## Decisión

**Nadie tiene los dos a la vez.** `Owner` es `Admin` con más privilegios y un valor mayor, así que otorgarlo reemplaza a `Admin`, y al revés. `Player` y `Master` siguen siendo acumulables con cualquiera y entre sí (#37). En autorización no cambia nada: cada endpoint de administración sigue escribiendo `hasAnyRole('ADMIN','OWNER')` y **no** se registra un `RoleHierarchy` (#89, CVE-2025-41248 en #123). Lo que cambia es el **otorgamiento**: la exclusión la aplica el service que asigna roles, y como ese service todavía no existe —llega con `/admin/users`, E2 sub-rebanada 5— la regla queda escrita en skill `arquitectura` §4.5 para que nazca con ella y no se agregue después

## Por qué

Precisa #89, que decía "`Owner` puede todo lo de `Admin`" sin cerrar si se podían tener los dos. Tenerlos juntos no significa nada: no suma un solo permiso, y sí ensucia todo lo que se pregunte por el rol —un usuario aparecería dos veces en un listado por rol, el `ContextSwitcher` tendría que deduplicar, y la ayuda de #170 mostraría la misma audiencia dos veces—. Es la única pareja de roles con esta relación: `Player` y `Master` describen funciones distintas y por eso se suman; `Admin` y `Owner` describen **la misma** función con distinto alcance, y de esas se elige una
