---
adr: 144
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0144: Bajar el shell al design system: fuentes, acento como texto y el toggle de tema

## Decisión

**Cuatro cosas, todas empujadas a la fuente de verdad en vez de resueltas en el JSX.** (a) **Las tipografías se autohospedan** con `@fontsource-variable/inter` y `@fontsource/spectral` (500/600/700), importadas en `main.tsx`. (b) **Nuevo token `--color-brand-fg`** — el acento *como texto*, que no es la misma escala que el acento *como relleno*: `brand-400` en oscuro, `brand-700` en claro. (c) **Nuevo token `--gradient-brand`** con el gradiente medido de la comunidad (#132), usado solo por `/login`, que por eso queda fuera de `PublicLayout` y arma su propio encuadre a sangre. (d) **El cambio de tema vive en el `UserMenu`** —donde ya lo ponía el inventario de skill `diseno` §5— con `enableSystem={false}`: "oscuro por defecto" (#131) es una decisión de diseño, no la preferencia del sistema operativo. El ítem nombra la acción ("Tema claro" cuando estás en oscuro), no el estado

## Por qué

El shell no se parecía al design system y cada motivo era distinto. **Las fuentes no se cargaban en absoluto**: el `@theme` declaraba Inter y Spectral pero nada las pedía, así que la app se veía bien solo en una máquina que ya las tuviera instaladas —la del desarrollador— y caía a `ui-serif`/`system-ui` en cualquier otra. Se eligió autohospedar sobre el `<link>` a Google Fonts que usan las previews de `build.py` porque evita una request bloqueante a un tercero y funciona sin red. **El token de acento-texto salió de medirlo**: el wordmark en dos tonos con `brand-400` da 5.71:1 en oscuro pero **3.29:1 en claro**, debajo de AA — y `brand-500` es al revés (3.74:1 en oscuro). Ningún tono único sirve para los dos temas, así que el que flipa tiene que ser un token, no una clase elegida a ojo; `build.py` ahora mide los cuatro pares (canvas y surface × los dos temas) y sale con código 1 si alguno baja de AA, igual que el resto. El gradiente es token por lo mismo: la alternativa era copiar cuatro hex a mano en el JSX, que es exactamente lo que #118 prohíbe. **Lo que no entró y es deliberado**: los filtros del explorador (necesitan catálogos, fuera de E1), el `SystemFeedbackDialog` del `UserMenu` (#133, sin endpoint todavía) y el sistema/plataforma/horario de la `GameTableCard` (sin datos en E1).
