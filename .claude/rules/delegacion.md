# Delegación por modelo — Opus decide, los agentes ejecutan

> Sin `paths:`: se carga siempre. La decisión y su porqué están en ADR-01 «Proyecto, proceso y conocimiento» §13 «Cada tarea de un plan dice con qué modelo se planea y con cuál se ejecuta»; las reglas de quien delega, en `ecc/common.md` §«Delegación a subagentes». Esta rule es la lista operativa.

**La sesión principal es Opus 5.5 y no carga con todo.** Antes de empezar cualquier tarea que no sea una respuesta corta, **se mide qué modelo le conviene** y se delega lo que no es de Opus:

| Modelo | Qué hace | Señal para elegirlo |
|---|---|---|
| **Fable 5.1** (agente con `model: fable`) | Planea lo más complejo | Cruza varias ADRs, toca el modelo de datos o la seguridad |
| **Opus 5.5** (la sesión, o un agente que hereda su modelo) | Planea lo demás, diagnostica bugs sutiles, escribe ADRs, tría lo que vuelve | Hay que decidir o juzgar, o el error es caro de deshacer |
| **Sonnet 5.5** (agente con `model: sonnet`) | Ejecuta lo ya decidido: bugs con causa clara, barridos, renombres, traducciones, ajustes de UI, recorridos con Playwright | La tarea se puede escribir como instrucciones sin decisiones abiertas |

- **Siempre en paralelo lo que no depende entre sí**: varios agentes en un mismo mensaje, uno por grupo de archivos o por feature. Se agrupa para que dos agentes no toquen los mismos archivos.
- **El prompt del agente se sostiene solo**: el objetivo, qué leer (`CLAUDE.md`, la sección de la skill), los archivos, el criterio de terminado y cómo se verifica.
- **Quien delega recoge**: espera, revisa lo que vuelve contra el código, integra y commitea. Un agente no commitea ni hace push.

## Varios agentes sobre el mismo árbol

- **Maven de a uno.** `./mvnw` compila sobre el mismo `backend/target`, y el backend levantado se reinicia con cada compilación (`entorno-local` §2). Todo agente que corre Maven toma antes un candado de directorio y lo suelta al terminar: `until mkdir <scratchpad>/mvn.lock 2>/dev/null; do sleep 10; done; ./mvnw …; rmdir <scratchpad>/mvn.lock`. Nunca `clean`.
- **Prettier solo sobre lo propio**: `npx prettier --write <archivos tocados>`, no `npm run format`, que reescribe archivos de otros agentes a mitad de su edición.
- **Sin la suite e2e entera**: comparte la base y el backend. Un agente corre solo el spec que tocó.
- **Si un `Edit` falla porque el archivo cambió**, se vuelve a leer: probablemente otro agente editó otra parte.
