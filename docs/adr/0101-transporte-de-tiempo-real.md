---
adr: 101
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0101: Transporte de tiempo real

## Decisión

**WebSocket + STOMP** (`/ws`), con el JWT en el frame `CONNECT` y no en la query string. Destinos: `/user/queue/notifications` para lo personal y `/topic/admin-queue` para la bandeja. **El mensaje es una señal de invalidación, no el contenido**

## Por qué

Se eligió WebSocket sobre SSE por dejar abierta la puerta a funciones bidireccionales (chat, presencia) sin cambiar de transporte, asumiendo a cambio más piezas: handshake, heartbeat y reconexión. El token va en el frame STOMP porque el navegador no puede mandar headers en el handshake y la query string queda registrada en los logs de acceso. Mandar solo la señal y dejar que TanStack Query refetchee mantiene la caché del cliente como única fuente (regla dura 11) y hace que un mensaje perdido no deje la UI inventando datos. **Límite conocido**: el broker en memoria de Spring sirve para una sola instancia; con más de una hace falta un broker externo.
