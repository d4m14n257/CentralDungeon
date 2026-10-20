---
adr: 64
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0064: Clasificación de los archivos públicos

## Decisión

**Los archivos públicos llevan un enum de audiencia** — a quién van dirigidos: masters, jugadores, publicaciones generales

## Por qué

Cierra **M24.1**. `getPublicFilesFromTable` los traía todos sin distinción, así que un archivo pensado para masters aparecía igual ante un jugador. Con la audiencia declarada, cada pantalla pide lo suyo y no hace falta filtrar a mano en cada consulta. Los valores exactos se cierran al implementar; los tres mencionados son el punto de partida.
