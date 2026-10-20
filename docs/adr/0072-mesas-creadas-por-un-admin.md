---
adr: 72
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0072: Mesas creadas por un admin

## Decisión

**Un admin puede crear una mesa sin ser su master. Nace en estado `Unassigned` y, al asignarle sus masters, pasa directo a `Opened` sin pasar por revisión**

## Por qué

Cierra **M28**. Saltear la revisión es lo correcto: por #27 quien aprueba una mesa es un admin, así que pedirle que apruebe la que él mismo creó no agrega control. `Unassigned` hace visible que a la mesa le falta dueño, en vez de dejarla en un `Preparation` que no describe su situación. Al asignar, el admin decide quién es `Primary` y quiénes `Secondary` (#71).
