---
adr: 213
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0213: GET /game-tables/{id}/players

## Decisión

**Un endpoint propio para el padrón de la mesa**, separado de `GET .../registrations`, que devuelve la cola de candidatos

## Por qué

Hasta F1.5 nada podía listar los jugadores de una mesa. El primero que lo necesitó fue el selector de una petición `Single`: dirigirle algo a una persona exige poder elegirla, y ofrecer el directorio completo de la plataforma sería ofrecer gente que no se puede elegir. No es un `?status=` sobre el endpoint que ya existe porque el orden de la cola de candidatos es una **regla de equidad** (#28) y el de un padrón no: un endpoint que responde dos preguntas con dos ordenamientos distintos es cómo alguien termina confiando en el equivocado
