---
adr: 205
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0205: Qué ofrece el selector de archivos publicados

## Decisión

**Todos los publicados, sin filtrar por la audiencia de quien elige**

## Por qué

Lo encontró el e2e: el picker del master filtraba por audiencia `Masters` y la hoja de personaje por defecto —publicada **para jugadores**— no aparecía, o sea que el ejemplo que motiva #79 no se podía ejecutar. La audiencia dice **quién termina leyendo** el archivo, no quién lo adjunta. Sigue siendo un filtro disponible (`publishedAudience`) para la pantalla que sí lo necesite, como el lado del jugador en F2
