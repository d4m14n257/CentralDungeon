---
adr: 259
estado: Aceptada
fecha: 2026-09-18
modifica: [100]
---

# ADR-0259: La bandeja son N consultas y un merge en Java, no un UNION ALL nativo

> Precisa #100

## Decisión

Una consulta por fuente, techo de 200 filas por fuente con `WARN` al tocarlo, merge y orden en el service: el que espera hace más tiempo primero, desempate por id. Se pagina en memoria, con `PageResponse` y total exacto (#173). Sumar una fuente en F5 es agregar un método privado.

## Por qué

#100 dice «un `UNION ALL` de cuatro consultas pequeñas normalizadas a un DTO común en el service», y lo que se construyó es eso **sin SQL nativo**: no hay un solo `@Query(nativeQuery = true)` ni un `UNION ALL` en todo el repositorio, y un union sobre cuatro tablas de columnas distintas sería el primero **y habría que reescribirlo en F5** al sumar fuentes. El precedente propio es `MasterDashboardService`, que resuelve el mismo problema —ítems heterogéneos ordenados por antigüedad— concatenando consultas privadas y ordenando en Java. Lo que la bandeja une es **trabajo pendiente, acotado por definición**: una bandeja con miles de ítems es un problema de gente, no de paginación. El techo se declara igual, porque un merge sin techo es memoria que nadie declaró. **Y qué entra es más angosto que lo que #176 sugiere**: de `game_tables` solo `Preparation` — `Draft` no (nadie la envió), `ChangesRequested` no (la pelota está en el master), `Unassigned` no (le falta master, no revisión). Fijado con tres `verify(never())`, que es lo que impide que una lectura laxa lo ensanche.
