# 3. Sistema de diseño

> Parte de la skill `diseno` (#273). Se movió desde `docs/frontend-diseno.md` §3 y **conserva su numeración**, que es la que citan el código y `decisiones.md`. Los valores concretos —colores, tipografía, espaciados, radios— no se escriben acá: salen de `design/build.py` (#130).

Tokens en el bloque `@theme` de `src/styles/globals.css`. Tailwind 4 no tiene `tailwind.config.ts`.

### Dirección visual

**Fantasía sobria** (#131): serif solo en títulos, sans en el cuerpo, densidad media. Se lee como herramienta seria, con un guiño al género.

La paleta **no se eligió, se midió** sobre los assets que la comunidad ya usaba (#132):

| Fuente | Qué dio |
|---|---|
| Gradiente de `links.centraldungeon.org` | `#214b90` · `#070c12` · `#211949` · `#3e308b` |
| Píxeles del logo y el favicon | Azul ~218° (27% de lo cromático) y violeta ~250° (17%). **Ningún píxel cálido** |

De ahí salen las dos decisiones que gobiernan todo lo demás:

- **Acento: el violeta de marca** (`#3e308b` y su escala). Se eligió sobre el azul aunque el azul sea el hue dominante, porque el azul choca con `state-active` (**InProgress**, el estado más frecuente) y el violeta solo con `state-paused` (**Pause**, excepcional).
- **Canvas oscuro: `#070c12`**, el negro-azulado de la propia comunidad, no slate neutro.

**El choque es inevitable y se contiene con separación de roles**, no con distancia de tono: los dos hues de la marca ya están ocupados por estados, así que el **acento aparece únicamente como relleno sólido** (botones, foco, karma) y los **estados únicamente como relleno suave con punto y etiqueta**. Nunca compiten en el mismo rol.

Los valores exactos no viven acá: los genera `design/build.py` y se publican en el design system (#130).

### Cómo se trabaja la paleta

`design/build.py` es la fuente de verdad. **Solo se versiona el script**; `design/out/` se regenera, igual que los PNG de `docs/diagramas/`.

```bash
python3 design/build.py                  # regenera out/ y mide los 30 pares de contraste
open design/out/accent-decision.html     # cualquier preview
python3 design/extract-brand-colors.py <logo.png> "LOGO"   # rehace la medición de #132
```

Qué produce en `design/out/`:

| Salida | Qué es |
|---|---|
| `theme.css` | El bloque `@theme` completo. **Es lo que se transcribe** a `frontend/src/styles/globals.css` |
| `accent-decision.html` · `colors.html` · `states.html` · `typography.html` | Los tokens, con la evidencia de marca y el contraste de cada par |
| `components.html` | Botones, badges, karma y `GameTableCard`, en ambos temas |
| `screen-*.html` | Las cinco pantallas de §4, en ambos temas |

**Cambiar un color es editar `build.py` y volver a correrlo**, nunca tocar el CSS generado. Cada corrida mide el contraste y **sale con código 1 si algo cae por debajo de AA**: una paleta que rompe accesibilidad no llega a publicarse.

### Tema claro y oscuro

Ambos, con `next-themes` o equivalente. El frontend viejo ya tenía `ColorModeContext`; es una función que se conserva.

**Oscuro por defecto** (#131) — la comunidad juega de noche. El claro se deriva del oscuro, no al revés, y los dos tienen que estar igual de terminados: E0.5 no se cierra con uno solo.

**Se cambia desde el `UserMenu`**, no desde una pantalla de configuración, y **sin opción "seguir al sistema"** (#144): que el default sea oscuro es una decisión de diseño, no la preferencia del sistema operativo de cada uno. El ítem nombra la acción, no el estado — estando en oscuro dice "Tema claro". La elección queda guardada. Como el menú solo existe con sesión, `/login` se ve siempre en oscuro; el gradiente de marca que lo cubre no depende del tema.

### Idioma

Dos, **español e inglés** (#198), y se elige en el mismo lugar y con la misma lógica que el tema:
desde el `UserMenu`, guardado en el navegador, sin pantalla de configuración.

**El primer idioma se calcula**, a diferencia del tema: se mira la elección guardada, después
`navigator.languages` por su subtag primario —`en-GB` y `en-US` son los dos inglés— y, si nada
coincide, **español**. Que el fallback no sea inglés es deliberado: la comunidad escribe y juega en
español, así que un navegador que no se reconoce es mucho más probable que sea de alguien que lo
habla.

**Cada idioma se nombra a sí mismo** en el selector —«Español», «English»—, nunca traducido al que
está activo: quien busca el suyo no necesariamente lee el que está viendo. `<html lang>` sigue al
idioma elegido, para que un lector de pantalla cambie de voz con la página.

**`/login` lleva su propio selector, dentro de la tarjeta**, y ahí no es un menú: los dos idiomas
están a la vista. Es la única pantalla sin `UserMenu`, y quien cae en un idioma que no lee no tiene
dónde buscar — un control plegado escondería justo lo que esa persona necesita encontrar. La
detección resuelve el caso común, pero no el de alguien cuyo navegador está en un tercer idioma, y
para ese la única salida es que se vea sin abrir nada.

El tema **no** se puede cambiar desde `/login` y sigue igual: el gradiente de marca que cubre esa
pantalla no depende del tema, así que no hay nada que arreglar ahí.

**El acento como texto es un token propio**, `--color-brand-fg`, distinto del acento como relleno: ningún tono único pasa AA en los dos temas (`brand-400` da 3.29:1 sobre el canvas claro). Cualquier texto en color de marca —el wordmark, el karma— usa ese token y nunca una escala elegida a mano.

### Colores de estado — lo que más se repite

Los nueve estados de mesa y los cinco de postulación aparecen en toda la aplicación. Se definen **una vez** como tokens semánticos; ninguna pantalla elige su propio verde.

| Estado de mesa | Token | Lectura |
|---|---|---|
| `Unassigned` | `--color-state-draft` | gris — existe pero le falta master |
| `Preparation` | `--color-state-pending` | ámbar — esperando a un admin |
| `ChangesRequested` | `--color-state-warning` | naranja — el master tiene que corregir |
| `Opened` | `--color-state-open` | verde — se puede postular |
| `InProgress` | `--color-state-active` | azul — está jugándose |
| `PauseRequested` | `--color-state-pending` | ámbar — esperando al admin |
| `Pause` | `--color-state-paused` | violeta — congelada |
| `Finished` | `--color-state-done` | gris azulado — terminó bien |
| `Canceled` | `--color-state-canceled` | rojo apagado — se cortó |

| Estado de postulación | Token |
|---|---|
| `Candidate` | `--color-state-pending` |
| `Player` | `--color-state-open` |
| `Rejected` | `--color-state-canceled` |
| `Blocked` | `--color-state-blocked` |
| `Deleted` | `--color-state-draft` |

**Accesibilidad**: el color nunca es el único portador de información. Cada badge lleva su etiqueta de texto, porque `Pause` y `PauseRequested` comparten familia de color y solo se distinguen leyendo.

### Karma

Escala 0–10000 con 8000 por defecto (#30). Se muestra como número con un indicador cualitativo, **sin** desglose agregado (#99):

```
Karma  8 240   ●●●●○     (basado en 12 comentarios)
```

El detalle son los comentarios listados debajo. Nada de gráficos: el rango real es angosto y una barra sugiere precisión que el número no tiene.

