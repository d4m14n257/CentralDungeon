---
adr: 179
estado: Aceptada
fecha: 2026-09-02
modificada-por: [275]
---

# ADR-0179: Administración de catálogos: se adelanta de F3 a F1

## Decisión

**`/admin/catalogs` completo entra en F1** — aceptar, clasificar, fusionar, separar y dar de baja (#55, #57, #59, #81)—, no solo el consumo y la propuesta que `plan-desarrollo.md` §4 le asignaba

## Por qué

Dejar la administración en F3 le abría a F1 un agujero silencioso: un master propone un tag, el tag queda en `Created`, y en `Created` **no filtra ni lo ven los jugadores** (#57). Sin nadie que pueda aceptarlo, F1 cerraría con valores propuestos que no sirven para nada hasta dos fases después, y el master vería su mesa etiquetada con algo invisible sin poder hacer nada al respecto. Es el mismo criterio que ordenó las fases (#177): la fase arrastra lo que su actor necesita, y proponer sin que exista aceptar no es una capacidad, es media capacidad. El costo es trabajo de admin dentro de la fase del master, y se asume: es una pantalla, no un subsistema
