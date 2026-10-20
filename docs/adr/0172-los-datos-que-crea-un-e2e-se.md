---
adr: 172
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0172: Los datos que crea un e2e se borran al terminar la corrida

## Decisión

`DELETE /api/v1/test-data/e2e`, solo bajo el perfil `test`, borra las mesas cuyo nombre contiene "E2E" —o que creó un usuario de prueba— y los usuarios con `discordId` que empieza con `e2e-`, en orden de claves foráneas. **No recibe parámetros**: los patrones están fijos en `TestDataService`, así que no hay entrada que agrande el alcance. Playwright lo llama en `globalTeardown` y avisa por consola cuántas filas se fueron; si no puede, no rompe la suite. Los specs se comprometen a nombrar sus mesas con "E2E" y sus usuarios con `e2e-` — es lo que los hace borrables

## Por qué

Cada corrida dejaba sus filas y la base de desarrollo se llenó: **47 mesas y 121 usuarios** acumulados al momento de escribir esto. No es solo ruido visual — una base llena fue lo que hizo fallar `registration-flow.spec.ts` cuando el explorador todavía no tenía orden (#171), y antes de eso ya había obligado a una limpieza a mano al cerrar E2 sub-rebanada 1 (#163). Un test que no limpia lo que ensucia deja de ser repetible: el segundo intento corre contra un mundo distinto del primero. Que el endpoint no reciba parámetros es a propósito: existe también en la base de desarrollo del perfil `dev,test`, así que la garantía tiene que estar en el código y no en quien lo llama
