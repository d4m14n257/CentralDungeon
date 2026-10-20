---
adr: 147
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0147: Quinto estado del retorno de OAuth: cuenta bloqueada

## Decisión

`sc_callback()` en `design/build.py` documentaba **cuatro** estados del retorno de OAuth (verificando, no-es-miembro, listo-segui, declinó-la-invitación) — ninguno era "tu cuenta tiene `status = Blocked`", que no tiene nada que ver con Discord ni con el guild. Se agregó como quinto paso (`e`), mismo tratamiento visual que los otros cuatro: tono `canceled` (reusa el token existente, sin agregar ninguno nuevo — cero cambios en `theme.css`), sin botón de acción porque reintentar no cambia nada. Del lado del frontend, `OAuthCallbackPage.tsx` pasó de cuatro bloques de texto suelto (sin card, sin badge) a un `CallbackCard` compartido — mismo componente para los cinco estados (incluido el de "verificando", que ahora también vive en el card)

## Por qué

El usuario lo notó probando el bloqueo real y dijo que no se parecía a nada del design system — tenía razón, literalmente no existía. Como los otros tres estados de la misma pantalla (no-es-miembro, error genérico, verificando) tampoco tenían el tratamiento de card+badge del mock, corregir solo el de bloqueado los habría dejado inconsistentes unos al lado de otros — se llevaron los cinco a `CallbackCard` de una vez, no por scope creep sino porque son la misma forma repetida en el mismo archivo. Verificado en vivo contra el flujo real de OAuth (perfil `test`, `TestDiscordController`), no solo el mockup: bloqueado y no-es-miembro, ambos con el chip de tono correcto. **Sumado en el mismo cambio**: el wordmark `CentralDungeon` arriba de cada card, igual que en `/login` — sin header en esta pantalla, es la única marca de que seguís en la app y no en un error suelto.
