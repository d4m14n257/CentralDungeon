---
adr: 138
estado: Aceptada
fecha: 2026-08-29
modificada-por: [277]
---

# ADR-0138: Alcance del responsive

## Decisión

**Responsive completo: las 27 rutas funcionan en teléfono, tablet y escritorio.** Puntos de corte de Tailwind sin inventar ninguno (`md` 768, `lg` 1024, `xl` 1280) y diseño **de menor a mayor**: las clases sin prefijo son las del teléfono. **Las tablas anchas dejan de ser tablas en móvil**: cada fila se vuelve una ficha con identidad y estado arriba, el resto como texto y la acción al pie. **Nunca scroll horizontal**. Los modales pasan a *sheet* desde abajo

## Por qué

Faltaba por completo: no había una sola mención a responsive ni en `frontend-diseno.md` ni en `arquitectura.md`, y las 20 pantallas dibujadas eran de escritorio. Es una comunidad que vive en Discord, donde la mayoría entra desde el teléfono, así que dejar el móvil afuera dejaba afuera el caso de uso más probable. Se evaluó acotarlo —móvil solo en el contexto Jugador, escritorio en Admin y Owner— y se descartó: parece más barato pero convierte "¿esta pantalla es responsive?" en una pregunta que hay que responder caso por caso, y la respuesta envejece mal cuando un admin quiere resolver la bandeja desde el celular un domingo. **El costo real está identificado y acotado**: `/admin/catalogs`, `/admin/users` y `/owner/audit` tienen cinco o más columnas, y por eso la regla de la tabla-a-ficha es una sola y explícita en vez de resolverse pantalla por pantalla. Decidirlo ahora y no después es lo que evita rehacer las 20 ya dibujadas.
