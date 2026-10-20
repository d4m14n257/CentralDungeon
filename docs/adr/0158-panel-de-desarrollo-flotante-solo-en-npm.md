---
adr: 158
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0158: Panel de desarrollo flotante, solo en npm run dev

## Decisión

Nuevo `components/dev/DevPanel.tsx`: un botón flotante (`fixed bottom-4 right-4`, ícono de matraz) que abre un `Popover` con dos pares campo+botón para `test-login` (Jugador y Master, cada uno con su propio Discord ID editable) y un botón para crear una mesa de prueba abierta. Reemplaza los `fetch()` de consola que documentaba `pruebas-e1.md`. Montado una sola vez en `RootLayout`, visible en cualquier pantalla incluida `/login`. Gateado con `if (!import.meta.env.DEV) return null` — Vite reemplaza esa constante por `false` en el build de producción y el minificador elimina el código muerto, así que no llega nada de esto al bundle real. `useCreateTable` es nuevo en `features/tables` (no exclusivo del panel: crear mesas es una capacidad real que E2 va a necesitar en una pantalla propia) con su `CreateGameTableRequest` propio, distinto de `GameTableDetail` porque la entrada usa `tableTypeId` donde la salida trae `tableTypeName`. Nuevo namespace `dev` en i18n, cumpliendo la regla 18 igual que cualquier otro texto visible. Se agregó también `components/ui/popover.tsx` vía `shadcn add popover` (usa `radix-ui`, ya instalado)

## Por qué

Pedido directo: el usuario probando E1 no quería seguir metiendo `fetch()` a mano en la consola del navegador cada vez que necesitaba cambiar de cuenta, y anticipó que más adelante va a necesitar login como Admin y Owner también — de ahí que el comentario del componente deje explícito que `test-login` necesita un parámetro de roles antes de que eso se pueda agregar, en vez de adivinar la forma ahora. La primera versión arrancaba con una cuenta inventada (`dev-tester`) en vez de las compartidas (`jugador-1`, `master-1`) que ya tienen datos reales armados en `pruebas-e1.md` — el usuario lo notó de inmediato porque la cuenta nueva no tenía notificaciones ni mesas, y se corrigió a dos campos independientes, cada uno precargado con la cuenta real correspondiente, editable para los casos de `pruebas-e1.md` que sí necesitan otro `discordId` (jugador fuera del servidor, cuenta bloqueada). No hay endpoint nuevo en el backend: el panel llama a `test-login` y a `POST /game-tables`/`open`, que ya existían.
