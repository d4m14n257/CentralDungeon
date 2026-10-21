---
adr: 145
estado: Aceptada
fecha: 2026-08-31
modifica: [103]
---

# ADR-0145: Detalle de mesa contra el design system, y matiz a #103 sobre el chip de contexto

## Decisión

**Dos cosas.** (a) `TableDetailPage` reescrita sobre la estructura de `screen-table-detail.html`: card `surface`, header con el badge de estado arriba a la derecha, la línea "Master: nombre · karma" (co-masters si los hay, karma en `--color-brand-fg`), separador, descripción, requisitos, **Cupo** (reusa `maxPlayers`/`playerCount`, ya tipados), y una barra de acción propia al pie con el botón a la derecha. **Nunca implementado**, no un regresión: no había ninguna versión anterior con esta estructura. (b) **Corrige #103**: con un solo contexto el chip se muestra igual, sin caret ni menú — antes no se mostraba nada

## Por qué

(a) Comparé el detalle real contra el mock y no se parecían en nada: sin bordes, "Masters" en lista vertical en vez de la línea inline, sin Cupo, botón pegado a la izquierda. **Quedó afuera la fila "Agenda"** del mock (día, hora, sesiones) y el "Cierra postulaciones en N días": lo primero es dato de `table_schedules`/conversión a hora local, que `plan-desarrollo.md` ubica en **F1** —la publica el master— y **F2** —la vive el jugador, en hora local— (#177), y `lib/date.ts` para esa conversión todavía no existe; lo segundo no es un campo del modelo en ningún lado, es solo texto de ambientación del preview. Meter cualquiera de los dos ahora es inventar un dato que no tiene de dónde salir. (b) #103 decía, literal, "no hay nada que elegir" y por eso nada se mostraba — cumplía la letra de la decisión, pero ningún mock de `build.py` ilustra ese caso (`shell()` siempre hardcodea `ctx="Jugador"`), así que la ausencia total se sentía como header roto en vez de deliberada. El usuario, al que le tocaba decidir porque es un cambio de comportamiento y no un bug, eligió mostrar el contexto igual. El caret desaparece a propósito: un chip sin menú que sigue pareciendo un botón desplegable es peor que uno sin señal de interacción — principio 2 de `diseno` §1 ("no se muestra lo que no se puede hacer") aplicado al revés, a lo que no se puede *interactuar*.
