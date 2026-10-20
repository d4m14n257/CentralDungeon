---
adr: 102
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0102: Idioma del código

## Decisión

**Todo el código en inglés, incluidos los comentarios**: identificadores, tablas, columnas, endpoints, Javadoc, logs, comentarios SQL de las migraciones y mensajes de commit. Documentación y conversación siguen en español

## Por qué

Antes la convención dejaba los comentarios en español, lo que producía archivos bilingües donde la firma de un método estaba en un idioma y su explicación en otro. Un solo idioma dentro del código lo hace legible para cualquiera que llegue, y deja el español para donde aporta: la documentación y las decisiones.
