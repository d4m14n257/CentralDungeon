---
adr: 131
estado: Aceptada
fecha: 2026-08-29
modificada-por: [132]
---

# ADR-0131: Dirección visual

## Decisión

**Fantasía sobria**, tema **oscuro por defecto**, densidad **media**. Serif solo en títulos, sans en el cuerpo. ~~Base neutra fría con un acento cálido metálico~~ — **corregido en #132 contra la marca real**

## Por qué

Se eligió sobre dos alternativas concretas. *Utilitario neutro* (shadcn casi por defecto) no daba ninguna identidad a una comunidad que sí la tiene. *Temático marcado* (pergamino, tinta, texturas) pelea de frente con la restricción más dura de esta interfaz: hay **catorce colores de estado** —nueve de mesa, cinco de postulación (skill `diseno` §3)— que tienen que ser distinguibles y accesibles en toda pantalla, y un fondo con personalidad los ahoga. Oscuro por defecto porque la comunidad juega de noche. La parte del acento cálido era una **inferencia sin evidencia** y resultó falsa: ver #132.
