---
adr: 249
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0249: Un perfil que caducó responde 404, igual que el veto

## Decisión

`GET /api/v1/users/{id}/profile` responde `404` cuando el actor no cumple ninguna de las reglas de #41, #44 o #47 — no `403` y no `200` recortado. La pantalla sí puede explicarle la caducidad a quien tuvo la relación, porque eso no le revela nada que no supiera.

## Por qué

Es la misma pregunta que #29 contestó para el veto, y las dos respuestas tienen que decir lo mismo o la diferencia entre ellas se vuelve un canal: un `403` afirma que esa persona existe **y** que hubo algo entre ustedes, que es precisamente lo que la ventana de dos semanas cierra. Quien quiera saber si fulano sigue en la plataforma probaría ids hasta ver un `403` en vez de un `404`. **El `200` recortado es peor por otra vía**: filtra la existencia igual y encima reparte la regla en dos lugares —qué campos faltan y por qué— con el frontend obligado a interpretar ausencias, que es cómo un día se muestra un perfil vacío en vez de no mostrarlo. **La asimetría con la pantalla no es una filtración**: el frontend sabe de sus propias mesas cerradas y puede decir «ya no podés ver este perfil» sin preguntarle nada al servidor; lo que nunca aprende es si el perfil existe para alguien más.
