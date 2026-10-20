---
adr: 149
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0149: Chip de "ya te postulaste" en la card del explorador

## Decisión

`GameTableCard` (`features/tables/`) suma un prop `alreadyApplied?: boolean` — no el estado real de la postulación (`Candidate`/`Player`), un booleano plano — y cuando es `true` pinta un chip neutro (`bg-raised`/`text-fg-muted`, ícono de check) junto al badge de estado de la mesa. Quien cruza mesas con postulaciones es `TableListPage.tsx`, la ruta: llama a `useMyApplications()` además de `useGameTables()`, arma un `Set` de `gameTableId` con estado `!== 'Rejected'`, y le pasa el booleano ya resuelto a cada card

## Por qué

Pedido directo del usuario probando el explorador: quería saber si ya se había postulado a una mesa sin tener que entrar a cada una — la lista como explicación (principio 4). El booleano en vez del estado real no es una simplificación de apuro: `GameTableCard` vive en `features/tables/` y la regla dura 16 prohíbe que una feature importe de otra — no puede traer `RegistrationStatus` de `features/registrations/`, ni el tipo ni el componente `RegistrationStatusBadge`. Y da la casualidad de que un booleano es exactamente el nivel de detalle correcto para lo que se pidió: "ya te postulaste" es cierto tanto si seguís `Candidate` como si ya te aceptaron (`Player`), así que no hace falta la granularidad que cruzar el tipo real habría traído. Se descartó pintarlo con el acento de marca (violeta): frontend-diseño.md §3 reserva el acento a relleno sólido —botones, foco, karma— y a los estados les toca el relleno suave con punto; este chip no es ninguna de las dos cosas (no es una acción, no es un estado de la mesa), así que se resolvió neutro en vez de forzarlo a un rol que no le corresponde. Verificado en vivo: se postuló un jugador de prueba a una sola mesa entre una lista de 13, y el chip apareció únicamente en esa card.
