---
adr: 50
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0050: Borrador nunca confirmado

## Decisión

**Expira.** Se marca con `deleted_at` y no avanza. Antes de expirar se notifica al autor: al terminar la mesa y otra vez antes de que cierre la ventana

## Por qué

Cierra **M17.2**. Confirmar es un acto deliberado y no se suple con silencio: publicar solo lo que nadie releyó era justo lo que #48 quería evitar. Las notificaciones reducen la pérdida a quien realmente no quiso confirmar. **Ver M18**: un borrador expirado que conserva autor y contenido es un registro de quién iba a decir qué sobre quién, así que el `deleted_at` necesita ir acompañado de purga del contenido.
