---
adr: 154
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0154: NotificationBell contra el mock, y siempre las últimas 5

## Decisión

**Dos cosas.** (a) El panel del botón de acceso rápido se reescribió sobre `sc_comp_shell` de `design/build.py`, que ya lo especificaba y nunca se había implementado así: encabezado "Notificaciones · N sin leer", cada fila con un punto lleno (acento, `--color-brand-fg`) y fondo `raised` si no está leída, punto hueco y texto atenuado (`fg-muted`/`fg-subtle`) si ya se leyó, hora relativa a la derecha ("ahora"/"N min"/"N h"/"N d", nuevo `lib/relativeTime.ts` que devuelve la unidad y el conteo — no el string ya armado, para que el texto visible siga saliendo de `t()` y no del código, regla 18), separador, y un `viewAll` en `--color-brand-fg` al fondo que ya existía como link pero con el texto equivocado (repetía el título). El mensaje de cada notificación se dejó de mostrar en el panel — el mock nunca lo tenía, solo título y hora — sigue visible en `/notifications`. (b) `notificationsApi.list()` ahora pide `size=5` siempre: antes el panel recortaba a 5 en el cliente pero `/notifications` no recortaba nada (mostraba la página completa del backend, hasta 20). Con el límite movido a la fuente ambas pantallas quedan consistentes sin lógica duplicada

## Por qué

El usuario probó el panel y no le gustó el diseño — comparado contra `design/build.py`, tenía razón: nunca se había construido según el mock, que ya traía la distinción punto lleno/hueco y la hora relativa desde antes de esta sesión. Pedido explícito de "como nota siempre pon los últimos 5", que ya se había aplicado al panel en el cambio anterior pero no a la página completa. Verificado en el navegador real (no solo por tipo): se generaron dos notificaciones de prueba para un master temporal y se inspeccionó el DOM — las filas no leídas resuelven a `rgb(27,36,56)` (`--color-raised`) con texto a full contraste, y "Ver todas" resuelve a `rgb(142,124,224)` (`--color-brand-fg` oscuro), exactamente los tokens del mock; el click en "Ver todas" navega a `/notifications`. Datos de prueba borrados de MySQL después de confirmar.
