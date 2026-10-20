---
adr: 99
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0099: Qué se muestra del karma

## Decisión

**El puntaje y los comentarios recibidos. Sin desglose agregado.** Lo mismo que ve el dueño de un perfil es lo que ve un master al evaluarlo, sujeto a las reglas de visibilidad de #41, #44 y #47

## Por qué

Se descartó mostrar un resumen tipo "X positivos, Y neutros, Z negativos": **los comentarios mismos son la explicación**, y leerlos dice más que contarlos. Además evita construir una segunda representación del karma que puede desincronizarse de la primera. Que ambos vean lo mismo elimina la pregunta de qué información privilegiada tiene cada uno.
