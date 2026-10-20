---
adr: 198
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0198: El idioma se elige como el tema, se detecta del navegador y cae en español

## Decisión

Dos idiomas, `es` y `en`, ambos en el bundle. `resolveInitialLanguage()`: primero la elección guardada en `localStorage`, después `navigator.languages` por subtag primario, y si nada coincide **español**. El selector vive en el `UserMenu`, al lado del tema, y **también dentro de la tarjeta de `/login`**, ahí con los dos idiomas a la vista y no plegados. Cada idioma se nombra a sí mismo («English», nunca «Inglés»)

## Por qué

La comunidad escribe y juega en español, así que un navegador que no se reconoce es mucho más probable que sea de alguien que lo habla — por eso el fallback no es inglés. Se guarda en `localStorage` y **no** en el modelo: el tema hace lo mismo, y skill `arquitectura` §4.7 dejó i18n fuera del modelo a propósito; meterlo ahora sería una columna, una migración y un endpoint para una preferencia del dispositivo. Cada idioma se nombra en sí mismo porque quien busca el suyo no necesariamente lee el que está viendo. `<html lang>` lo sincroniza `providers/i18n.ts` y no el hook, porque tiene que valer también en `/login`. Y `/login` **muestra los idiomas sin plegar**: es la única pantalla sin `UserMenu`, y esconder el control detrás de un menú se lo escondería justamente a quien no puede leer lo que hay en pantalla — que es la única persona que lo va a buscar. **Los tests fijan el idioma** (Vitest en el setup, Playwright en la config): sin eso una aserción sobre una etiqueta pasaba o fallaba según el locale de la máquina que corriera la suite
