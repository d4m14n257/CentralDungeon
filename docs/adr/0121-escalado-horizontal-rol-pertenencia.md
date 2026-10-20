---
adr: 121
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0121: Escalado horizontal — rol ≠ pertenencia

## Decisión

**Toda lectura o mutación de un recurso concreto filtra por el actor**: o el actor entra en el `WHERE`, o el service verifica pertenencia antes de tocar nada. Nunca `findById(id)` + `save()` sin comprobar

## Por qué

Es el agujero que tenía el Node y que ninguna capa de autenticación habría tapado: `UPDATE Tables SET … WHERE id = ?` y `UPDATE Tables SET status='Deleted' WHERE id = ?`, sin un solo predicado de pertenencia. Con login y todo, cualquiera que conociera el id de una mesa podía editarla o borrarla. `hasRole('MASTER')` afirma "es master de algo", no "de *esta* mesa": son dos comprobaciones distintas y la segunda no se puede delegar en Spring Security. Que los ids sean impredecibles (#9) es defensa en profundidad, no autorización — un id filtrado en un link no puede ser lo único que proteja un recurso.
