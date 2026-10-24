---
paths:
  - "frontend/**"
---

# Frontend — reglas duras

> Se cargan al tocar `frontend/`. Conservan el número de regla dura que les da `CLAUDE.md`, que es el que citan los docs (el código no, ADR-01 «Proyecto, proceso y conocimiento» §12 «La documentación del código se explica sola»). El detalle y el porqué están en la skill `arquitectura` §3, §5.2 y §6.2, y lo visual en la skill `diseno`: las dos se leen antes de escribir una pantalla o un componente. Lo que suma ECC para TypeScript, en `ecc/typescript.md`.

**6.** **Un espejo escrito a mano por cada `…Response`**, en el `types.ts` de su feature; lo que arma el frontend (payloads, vistas, borradores) se deriva con utility types (`Pick`, `Omit`, `Partial`, `Record`…) del tipo que ya existe, nunca se re-declara a mano (ADR-02 §3, `arquitectura` §3.2). Nunca `any`.

**7.** Toda regla de negocio nueva llega con su test: Vitest si la pantalla o el hook tienen lógica, Playwright contra el backend real para los flujos críticos (`arquitectura` §5.2, §5.3).

**11.** Datos de servidor solo con TanStack Query. Nada de `useEffect` + `fetch`, nada de respuestas de API en Context o Zustand. HTTP, solo por `api/client.ts`.

**16.** **Una feature nunca importa de otra.** Las pantallas van en `src/routes/` y son el único lugar que compone dominios; cada bloque de una pantalla compuesta recibe un **id**, no una entidad (§3.1.5). Cada feature expone su superficie en `features/<dominio>/index.ts`. Lo sin dominio va en las capas de la raíz: `components/`, `hooks/`, `lib/`, `api/`, `types/`, `config/`. Una capa de la raíz puede importar el barrel de una feature, nunca una ruta interna; `features/help` nombra tipos de otras solo con `import type` (ADR-19 §1).

**18.** Ningún string visible se escribe en el JSX: todo pasa por `t('espacio.clave')` con el JSON en `src/locales/<idioma>/` (ADR-23 §1). **Toda clave nueva se agrega en `es` y en `en`, en el mismo commit** (ADR-23 §2): un idioma a medias muestra la clave cruda. Y ningún valor de estilo suelto: los tokens salen del `@theme`, que se transcribe desde `design/build.py` (ADR-21 §1). **Un patrón de estilo tiene nombre** (ADR-21 §5): una combinación de utilidades que cumple un papel de diseño —título de página, acciones de fila, lista con divisores— no se escribe literal en una pantalla; es una clase de `styles/base.css` o un componente de `components/`, catalogado en la skill `diseno` §5.c.

`components/ui/` es código generado por el CLI de shadcn: no se edita a mano.

Antes de dar algo por terminado: `npx tsc -b` (el typecheck del hook de ECC no revisa nada aquí), `npm run test` y `npm run format`.
