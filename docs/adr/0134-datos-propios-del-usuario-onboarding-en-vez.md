---
adr: 134
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0134: Datos propios del usuario — onboarding en vez de pantalla de configuración

## Decisión

**No hay pantalla de edición de perfil en v1.** `name` y `country` se piden **una sola vez**, en un paso bloqueante después del login inicial (`/onboarding`), inmediatamente después de la invitación al servidor de #38. `name` viene precargado con el `discord_username` y se puede cambiar. `country` pasa de `VARCHAR(64)` a **`CHAR(2)` ISO 3166-1 alpha-2**. `discord_username` se conserva siempre

## Por qué

El disparador fue encontrar que **ninguna de las dos columnas tenía quién la llenara**: Discord no expone país en el OAuth, y el legacy —cuatro `GET` y ni un endpoint de escritura en su router de usuarios— nunca pudo escribir ninguna de las dos. Eran columnas muertas heredadas. Se descartó una pantalla de configuración porque el contenido real editable son **dos campos**, y una ruta entera para eso no se paga. Se descartó eliminarlas porque el objetivo es legítimo y concreto: **que cada quien se llame como quiera dentro del sistema, sin perder el rastro de cuál es su Discord** — por eso `name` es display y `discord_username` se conserva como identidad verificable para admins. El paso bloquea a propósito: si se pudiera saltar, las columnas volverían a quedar nulas y el problema seguiría existiendo. `CHAR(2)` en vez de texto libre evita el desorden de *México* / *Mexico* / *MX* y habilita la bandera como emoji derivado del ISO, sin llamar a ningún servicio externo — que es lo que `getFlagCountry.js` hacía mal en el legacy (`frontend-diseno.md` §6). **Editar después queda fuera de v1, no descartado**: cuando se agregue, es una pantalla que reusa el mismo formulario, sin tocar el schema.
