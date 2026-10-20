---
adr: 97
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0097: Cuándo se recalcula el karma

## Decisión

**Dos disparadores: al aprobar un comentario se recalcula el karma de esa persona, y un job semanal nocturno recalcula a todos**

## Por qué

El karma es un valor derivado que **cambia sin que ocurra ningún evento**, porque los pesos decaen con el tiempo. El recálculo puntual da respuesta inmediata donde importa —el afectado ve el efecto al momento— y el job semanal aplica el decaimiento sin recorrer toda la base cada noche. La disparidad entre ambos es despreciable: con `H = 12 meses` el peso de una valoración cambia **1,3% por semana**, unos pocos puntos sobre 8000.
