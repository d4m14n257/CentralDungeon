---
adr: 41
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0041: Visibilidad de perfiles y karma

## Decisión

**Asimétrica: el perfil de un master (karma + comentarios) es visible para cualquier usuario que mire su mesa; el perfil de un jugador solo se abre para el master a partir del momento en que recibe su solicitud**

## Por qué

El karma existe para evaluar comportamiento, y evaluar requiere ver. Pero exponer el perfil de todos a todos convierte la plataforma en un ranking público de personas. La asimetría refleja quién necesita decidir qué: el jugador decide **si vale la pena entrar** a una mesa y para eso mira al master antes de postularse; el master decide **a quién acepta** y para eso mira al candidato, pero solo cuando ese candidato se le presentó por su cuenta. Enviar la solicitud es lo que habilita la evaluación. Es una regla de privacidad, no solo de UI: el backend la aplica y el frontend la refleja.
