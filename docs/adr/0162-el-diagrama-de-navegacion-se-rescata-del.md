---
adr: 162
estado: Reemplazada
fecha: 2026-09-01
reemplazada-por: [266]
---

# ADR-0162: El diagrama de navegación se rescata del artifact de revisión, vivo en docs/diagramas/

## Decisión

Nuevo `docs/diagramas/18-navegacion.mmd`, agregado a la sección "Flujos" del índice. A diferencia del resto de los `.mmd` — ER del modelo objetivo o ciclos de vida, todos snapshots de una decisión ya cerrada — este es el único diagrama que se declara **vivo**: se espera que cada etapa lo actualice a mano cuando conecte o desconecte pantallas, para poder ver crecer la navegación real de una etapa a la otra en vez de perder ese registro

## Por qué

El artifact "Rebanada E1" —el mapa de navegación armado para guiar la ronda de pruebas manuales de esta etapa— cumplió su función de checklist y el usuario pidió cerrarlo, pero señaló que el diagrama de flujo en sí valía la pena rescatar para E2. Un artifact no es el lugar para eso: no versiona, no se relaciona con el resto de `docs/`, y el proyecto ya tenía la convención exacta para este caso (`.mmd` sin PNG versionado, `docs/diagramas/README.md` §Flujos). Se llevó tal cual estaba verificado contra el código real (`router.tsx` y cada `Link`/`navigate`), sin el maquillaje de "cierre de E1" que tenía en el artifact —esa parte es efímera y ya vive en esta misma entrada—, y se agregaron dos nodos futuros que el artifact no traía (`/master` de E2, dashboard) para que la próxima etapa tenga dónde enganchar sus propias flechas nuevas sin redibujar el diagrama entero.
