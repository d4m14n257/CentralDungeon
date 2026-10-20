---
adr: 195
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0195: Marcar una sesión como jugada es una acción explícita, separada de registrar la asistencia

## Decisión

`POST /sessions/{id}/hold` mueve `Scheduled` → `Held`. `PUT /sessions/{id}/attendance` no toca el estado de la sesión

## Por qué

Son dos hechos distintos: «jugamos» y «vino esta gente». Deducir el primero del segundo dejaría marcada como jugada una sesión que se suspendió y para la que el master igual cargó quién había avisado, y deducirlo al revés obligaría a inventar un padrón para poder cerrar una sesión. Cuesta un clic más y a cambio ningún registro dice algo que nadie afirmó
