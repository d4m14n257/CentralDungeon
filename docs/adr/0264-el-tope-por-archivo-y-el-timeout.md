---
adr: 264
estado: Aceptada
fecha: 2026-09-21
---

# ADR-0264: El tope por archivo y el timeout de la bandeja los publica un endpoint propio, GET /settings/limits

## Decisión

Un `record` con nombre (`ClientLimitsResponse`) para cualquiera con sesión, fuera de `/admin`, leído por `hooks/useClientLimits` con los defaults de `config/limits.ts` como respuesta mientras el pedido viaja. El servidor sigue aplicando el límite y contestando `FILE_TOO_LARGE` con el número real (#197)

## Por qué

**Sin esto la rebanada no cumple lo que promete.** Hasta F3.5 el tope era una constante de los dos lados que coincidía por casualidad; en cuanto un admin lo mueve sin desplegar, el espejo del frontend se vuelve un cliente que rechaza archivos que el servidor aceptaría — y el `Se prueba:` de la rebanada, «la subida siguiente lo respeta sin reiniciar nada», sería falso, porque el archivo nunca llegaría al backend. Va en `hooks/` y no en una feature porque `features/files` lo lee y `features/settings` lo edita, y una feature nunca importa otra (regla dura 16): lo compartido no es dominio sino configuración de plataforma, el mismo estante que `useBackendStatus`. Y `config/adminQueue.ts` ya lo había dejado escrito para el timeout de la reserva —«el día que deje de ser constante allá deja de serlo acá»—, así que los dos viajan juntos y ese archivo se reemplaza por `config/limits.ts`. El cupo máximo **no** viaja: ninguna pantalla lo enuncia antes de romperlo todavía, y un campo que nadie renderiza es el huérfano de #255
