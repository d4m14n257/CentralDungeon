---
adr: 157
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0157: El hover de una notificación no se notaba

## Decisión

`DropdownMenuItem` trae `focus:bg-accent` por defecto para resaltar al pasar el cursor, pero `--color-accent` **es** `--color-raised` en `globals.css` (una referencia directa, no un valor propio) — el mismo tono que ya se le puso a las filas no leídas en #156. Resultado: pasar el cursor sobre una notificación no leída no cambiaba nada visible, y la base también trae `cursor-default` en vez de `cursor-pointer`, así que tampoco había señal de que la fila fuera clickeable. Se corrigió en los tres lugares que renderizan una notificación (panel del `NotificationBell`, su ítem "Ver todas", y las filas de `/notifications`) con un tono de hover propio y más fuerte —`border-strong`, un escalón más oscuro que `raised`, con `!` para garantizar que gane sobre el `focus:bg-accent` de base sin depender del orden de generación de Tailwind— más `cursor-pointer` explícito

## Por qué

El usuario probó el panel rediseñado en #156 y notó que pasar el cursor no daba ninguna señal de selección. Confirmado leyendo el token en `globals.css`, no asumido: `--color-accent: var(--color-raised)` es una igualdad literal en las dos variables, en los dos temas. Verificado en vivo con Playwright leyendo `getComputedStyle(el).backgroundColor` antes y después de `hover()`, en los tres lugares: pasa de `rgb(27,36,56)` (raised, no leída) a `rgb(43,56,84)` (border-strong) en el panel; de `rgb(27,36,56)`/`rgba(0,0,0,0)` (no leída/leída) a un `border-strong` translúcido en `/notifications`. El snapshot de accesibilidad además confirma `[cursor=pointer]` en las tres filas tras el cambio. Datos de prueba borrados de MySQL después de confirmar.
