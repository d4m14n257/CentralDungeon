---
adr: 228
estado: Aceptada
fecha: 2026-09-09
---

# ADR-0228: La duración es de cada franja, la grilla es la única forma de armar la agenda, y los archivos entran al crear

## Decisión

**Cuatro cosas de un mismo recorrido.** **(a) Cambio de modelo**: `duration` deja `game_tables` y pasa a `table_schedules`. Cada franja dura lo suyo, así que una mesa puede correr tres horas entre semana y seis el sábado. Migración V7: se agrega la columna, se rellena desde la mesa y se **borra** la vieja. #178 mide con la duración de cada franja; una franja sin duración no reclama intervalo, que es lo que antes significaba una mesa sin duración. **(b) Una sola forma de armar la agenda**: se va el campo de duración del formulario y se va el mini-formulario de día y hora. La grilla dice **cuándo** y la lista de franjas dice **cuánto**, que son dos preguntas distintas. **(c) Los archivos ganan su propio paso**, el cuarto de cinco: se eligen mientras la mesa no existe y se enganchan apenas se crea, compartidos con los jugadores por defecto. **(d) Los campos de catálogo se marcan solos** —etiqueta, borde y mensaje propio— y el mensaje global desaparece en ese paso. Crear y editar quedan centrados

## Por qué

Todo salió de que el usuario creara una mesa. **(a)** Lo pidió textual: *«poder modificar duraciones de cada bloque»*. Se le advirtió que era el modelo y no la pantalla, y lo eligió igual. Resultó más chico de lo temido: **las sesiones no usaban la duración** —guardan un instante, no un largo (#33)—, así que materializar un calendario no se toca y lo único que la columna alimentaba era #178. Se borra `game_tables.duration` en la misma migración: dejar una columna que nadie lee habría sido peor que el cambio. **(b)** El síntoma que reportó fue *«pierdo control al seleccionar duración y uno se confunde viendo que se puede por form o por el schedule»*: dos formas de hacer lo mismo, y el master teniendo que adivinar a cuál le iba a creer la mesa. **(c)** Los archivos ya se podían adjuntar **después** de crear, en la pestaña Archivos; lo que faltaba era durante. Paso propio y no una sección dentro de otro: es una decisión propia —qué le doy a los jugadores— y plegada en el paso de revisión sería lo que nadie mira, que es exactamente cómo pasó desapercibido. `useAttachTableFile` toma el id de la mesa al construirse y por eso no servía; el hook nuevo lo toma al llamarse. **(d)** *«marcar los campos que no tienen un tag para que al usuario se le haga más fácil, no solo un mensaje en rojo hasta abajo»* — y al marcarlos, el mensaje de abajo pasó a decir lo mismo dos veces, así que en ese paso se va. **Consecuencia que apareció sola y es una mejora**: el e2e del choque de horarios (R1 de #178) dejó de poder reproducirse desde la interfaz, porque **la hora que otra mesa ocupa ya no se ofrece**. El test pasa a fijar eso —la hora no está y el bloque dice qué mesa la tiene—, y el rechazo del servidor queda cubierto por los tests del backend. Prevenir es mejor que rechazar.
