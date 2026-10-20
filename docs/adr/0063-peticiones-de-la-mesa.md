---
adr: 63
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0063: Peticiones de la mesa

## Decisión

**Una mesa puede pedirle cosas a sus jugadores en cualquier momento —antes de entrar, al entrar y durante las sesiones— y cada petición se responde con texto enriquecido, con archivos, o con ambos**

## Por qué

Unifica lo que venían siendo tres cosas separadas: los requisitos que el master publica al abrir la mesa (#60 uso 1), lo que el jugador manda al postularse (#60 uso 2) y lo que el master pide después de aceptarlo (**M23**). Las tres tienen la misma forma —alguien pide algo, alguien responde con texto y/o archivos— así que se modelan una sola vez. La analogía es un salón de clases: entrás a la mesa y te piden cosas, a veces atadas a una sesión y a veces sueltas. Es **funcionalidad nueva**, no algo que el schema heredado insinuara. Diseño en la sección *Peticiones de la mesa*.
