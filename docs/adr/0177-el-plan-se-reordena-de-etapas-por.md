---
adr: 177
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0177: El plan se reordena: de etapas por subsistema a fases por actor

## Decisión

`E1`…`E6` se reemplazan por **F1 Master, F2 Jugador, F3 Admin y Owner, F4 Comunidad, F5 Operación**. Cada fase entrega la experiencia completa de un actor y **arrastra los subsistemas que ese actor necesita** —los catálogos y los archivos entran con el master porque su mesa los pide, no como fases propias—, con **el mínimo del otro lado para poder probarla**: si el master publica una agenda, el jugador la ve en la misma fase. El orden es quien produce, quien consume, quien administra. **`E0`–`E2` se conservan como historia** —son el registro de lo construido— y `E3`–`E6` se borran, con una tabla de equivalencia en `plan-desarrollo.md` §4 que dice dónde fue a parar cada promesa. Las referencias ya escritas a las etapas **no se reescriben**: "E1 construyó X" es un hecho fechado; solo se actualiza lo que prometía algo para una etapa que ya no existe

## Por qué

El corte por subsistema repartía a cada actor entre cuatro etapas: el master recibía su wizard en E2, su agenda y sus peticiones en E3 y sus archivos en E4, y entre medio no había forma de probar una mesa completa. Eso es lo que genera conflictos al conectar: cada etapa dejaba a la siguiente piezas a medio encastrar. Por actor, en cambio, cada fase termina con **alguien que puede usar el sistema entero para lo suyo**, que es la unidad que de verdad se puede probar y mostrar. El master va primero porque **fabrica lo que los demás consumen** —sin mesas no hay nada que jugar ni que moderar—, y el legacy ya se había construido en esa dirección: `tables.ts` era 1065 de sus 1735 líneas, mientras comentarios, karma y notificaciones nunca existieron. El riesgo del orden nuevo es construir para un actor que nadie observa, y por eso la regla del mínimo del otro lado no es un adorno: es lo que impide repetir el callejón que E1 documentó cuando `/my/tables` faltaba. El schema ayuda: las 33 tablas del baseline ya existen, así que reordenar el trabajo casi no toca migraciones. **Corregida por #250 en la numeración**: entró F4 Revisión, así que las cinco fases pasaron a seis y las dos de esta fila corrieron — Comunidad es **F5** y Operación es **F6**
