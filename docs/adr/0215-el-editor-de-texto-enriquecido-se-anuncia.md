---
adr: 215
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0215: El editor de texto enriquecido se anuncia como campo de texto

## Decisión

**`role="textbox"` y `aria-multiline` explícitos en el `contenteditable` de TipTap**

## Por qué

Chrome expone ese `div` como un grupo cualquiera, así que un lector de pantalla llega a un área de edición que nunca dice que se puede escribir en ella, y sin `aria-multiline` la lee como campo de una línea —donde Enter significa "enviar"—. Lo detectó el e2e de F1.5 al no poder encontrar el editor por rol, que es la misma búsqueda que hace la tecnología asistiva: **si el test no lo encuentra por su rol, una persona con lector de pantalla tampoco**
