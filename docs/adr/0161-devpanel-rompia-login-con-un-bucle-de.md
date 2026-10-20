---
adr: 161
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0161: DevPanel rompía /login con un bucle de reload - useMe() sin gatear

## Decisión

`DevPanelContent` llamaba `useMe()` sin condición, y el panel está montado en la rama compartida de `RootLayout` que incluye `/login` (a propósito, para loguearse rápido desde ahí). Sin sesión, `useMe()` pega a `/users/me`, recibe 401, `client.ts` intenta `refreshSession()`, falla (no hay cookie de refresh), y hace `window.location.assign('/login')` — una recarga completa de la página. Esa recarga vuelve a montar el panel, que vuelve a llamar `useMe()`, que vuelve a fallar igual: bucle. Se corrigió pasando `useMe(isAuthenticated)` (el segundo parámetro `enabled` que el hook ya exponía desde antes, sin usar en ningún lado) usando `useAuth()` de `AuthProvider`

## Por qué

Lo encontró correr `npx playwright test` al cerrar la etapa, no una revisión manual: `discord-login.spec.ts` (dos casos) empezó a fallar con "element was detached from the DOM, retrying" al clickear "Entrar con Discord" en `/login` — sospechoso porque `registration-flow.spec.ts` seguía pasando y nada de esta sesión tocó el login. Sacando la traza se vio la recarga en bucle. Esto **no era un problema de los tests**: cualquier persona real visitando `/login` sin sesión válida se habría encontrado la página recargándose sola indefinidamente, apenas se desplegara `DevPanel` — un bug de producción real, aunque el panel en sí nunca llega a producción (`import.meta.env.DEV`), porque el mismo código sí corre en el `npm run dev` que se usa para probar. Verificado con la suite completa otra vez después del fix: los tres specs de Playwright, los 27 de Vitest y los 61 de backend (unitarios + integración con Testcontainers vía colima) en verde.
