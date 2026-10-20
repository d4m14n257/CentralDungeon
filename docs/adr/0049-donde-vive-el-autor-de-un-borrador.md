---
adr: 49
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0049: Dónde vive el autor de un borrador

## Decisión

**En `comment_drafts` y solo ahí.** Tabla privada, visible únicamente para su autor. Al confirmar se crea la fila anónima en `comments` y el borrador desaparece

## Por qué

Cierra **M17.1**. Es lo que hace compatible el borrador de #48 con el anonimato de #43: la autoría existe mientras el comentario todavía no cuenta para nada, y deja de existir exactamente cuando empieza a contar. Ninguna otra tabla —ni `comments`, ni `audit_logs`, ni `notifications`— guarda jamás quién escribió qué.
