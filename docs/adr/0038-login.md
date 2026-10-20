---
adr: 38
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0038: Login

## Decisión

**Requiere cuenta de Discord y membresía al servidor de CentralDungeon.** Si el usuario no es miembro, primero se le ofrece unirse; si no acepta, no hay login

## Por qué

Es la puerta de entrada de la comunidad: la aplicación no tiene registro propio y no debería tener usuarios que no estén en el servidor. El flujo importa: no se rechaza con un error seco, se ofrece la invitación y recién si el usuario la declina se corta. Todo usuario nuevo se crea con rol `Player` (reemplaza al trigger `create_new_user`, ver skill `arquitectura` §4.5).
