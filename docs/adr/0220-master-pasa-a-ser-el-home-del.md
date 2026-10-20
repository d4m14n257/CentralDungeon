---
adr: 220
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0220: /master pasa a ser el home del contexto Master

## Decisión

**El `ContextSwitcher` y el logo llevan a `/master`, no a `/master/tables`**, y aparece `MasterSectionNav` con *Lo que espera* y *Mis mesas*

## Por qué

#151(b) dejó el logo apuntando al home del contexto activo y anotó que en Master ese home era `/master/tables` **porque era lo único que existía**. Con la bandeja construida, el home es la pantalla que dice qué hacer a continuación, que es el punto de #136. La barra de secciones es calcada de `AdminSectionNav` y por el mismo motivo que aquella: con dos pantallas en el contexto, sin una forma de cruzar entre ellas una de las dos es una ruta que solo alcanza una URL escrita a mano.
