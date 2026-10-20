---
adr: 132
estado: Aceptada
fecha: 2026-08-29
modifica: [131]
---

# ADR-0132: Paleta de marca — corrige #131

## Decisión

**La marca es fría: azul profundo (~218°) y violeta índigo (~250°) sobre negro-azulado.** El acento de interfaz es el **violeta**, `#3e308b` y su escala. El canvas oscuro es `#070c12`, el negro de la propia comunidad, no slate neutro

## Por qué

No es una elección estética sino una **medición** sobre los assets que la comunidad ya usa —`links.centraldungeon.org`, su único espacio propio hasta que exista este sistema—, y dos fuentes independientes coinciden. El CSS del sitio: `linear-gradient(-45deg, #214b90, #070c12, #211949, #3e308b)`. Los píxeles del logo y el favicon, decodificados y agrupados por tono: dos familias, ~218° con el 27% de lo cromático y ~250° con el 17%, y **ningún píxel cálido**. Eso invalida el acento cálido de #131 y, de paso, disuelve el choque con `state-pending` y `state-warning` que ese acento arrastraba. **Aparece uno nuevo**: los dos hues de la marca ya están ocupados —`state-active` (InProgress) es azul y `state-paused` (Pause) es violeta—, y no hay acento de marca que lo evite. Se eligió el violeta sobre el azul con un criterio de costo, no de gusto: **el azul choca con `InProgress`, el estado más frecuente de la plataforma; el violeta choca con `Pause`, que es excepcional**. El choque residual se contiene con separación de roles —el acento solo aparece como relleno sólido (botones, foco, karma), los estados solo como relleno suave con punto y etiqueta— y está **verificado con contraste medido: 30 pares, 30 en AA**. Descartado usar el azul pese a ser el hue dominante (27% contra 17%): la frecuencia en el logo no compensa colisionar con el estado por defecto de media aplicación, y el violeta además es el más distintivo de los dos. **Las tipografías del sitio (Dosis, Abel, Handlee) no se adoptan**: Dosis en peso 200 con `letter-spacing: 3px` sirve para seis links, no para tablas de candidatos ni formularios.
