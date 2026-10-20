---
adr: 100
estado: Aceptada
fecha: 2026-08-29
modificada-por: [259]
---

# ADR-0100: Bandeja compartida de admins

## Decisión

**No se duplica el trabajo pendiente como notificaciones: la bandeja es una vista sobre las tablas donde ese trabajo ya vive.** Se agregan `claimed_by`/`claimed_at` a `approval_requests`, `comments`, `system_feedback` y `game_tables`; el ítem reservado desaparece para el resto y una reserva sin resolver se libera sola a los 15 minutos

## Por qué

Que "si la toma uno baja para todos" sale gratis si la bandeja lee el estado real en vez de una copia: cambia la fila, no un duplicado que después hay que mantener sincronizado. Se descartó una tabla `admin_queue` que agrupara los ítems porque sería una denormalización mantenida a mano — exactamente lo que #11 eliminó al matar `Players_Table`. El precio es un `UNION ALL` de cuatro consultas pequeñas, normalizadas a un DTO común en el service. La liberación por timeout evita que un ítem reservado y abandonado quede invisible para siempre.
