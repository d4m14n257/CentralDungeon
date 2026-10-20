---
adr: 65
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0065: Reutilización de archivos

## Decisión

**El usuario accede a todo lo que subió en cualquier momento de su historia y puede volver a usarlo.** No hay una "biblioteca" separada del resto de sus archivos

## Por qué

Cierra **M25.1** y es la principal palanca de optimización de costo: la hoja de personaje que un jugador ya subió se adjunta de nuevo en vez de volver a almacenarse. Un archivo es de quien lo subió y puede vincularse a cuantos contextos haga falta: lo que cambia es el vínculo, no el archivo. **Acotado por #68**: la reutilización aplica a los `Private` —lo que el usuario eligió guardar—, mientras que los `Single-use` siguen siendo transitorios.
