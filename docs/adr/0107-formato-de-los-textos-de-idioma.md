---
adr: 107
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0107: Formato de los textos de idioma

## Decisión

**JSON, no XML**, con `i18next` en vez de un cargador propio

## Por qué

El frontend viejo declaraba el espacio de nombres y sacaba el valor de un XML con un helper propio, que quedó a medias. JSON es nativo del ecosistema: no necesita parser, se tipa con TypeScript, y `i18next` ya resuelve interpolación, plurales, fallbacks y carga por espacio de nombres — todo lo que habría que escribir a mano. El XML no aporta nada a cambio. **Cuándo entra: desde la primera etapa (#117).**
