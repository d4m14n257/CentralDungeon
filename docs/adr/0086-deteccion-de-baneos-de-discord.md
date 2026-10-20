---
adr: 86
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0086: Detección de baneos de Discord

## Decisión

**En v1 lo marca un admin a mano**: cuando alguien es expulsado del servidor, un admin le pone `status = 'Blocked'` en la aplicación. Automatizarlo queda para cuando llegue el bot

## Por qué

Cierra **M30**. No es realmente una elección: con solo OAuth2 la aplicación puede preguntar si alguien **es miembro** del servidor y nada más — el que se fue y el que fue baneado devuelven lo mismo. Consultar los baneos exige un bot con permisos, que está explícitamente fuera de v1. La alternativa de no marcar nada dejaría el `status = 'Blocked'` de #84 sin nadie que lo aplique, y con él la distinción entre irse y ser expulsado. La casilla vive en el panel de moderación que los admins ya tienen por #51.
