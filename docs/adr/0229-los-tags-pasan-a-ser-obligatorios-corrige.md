---
adr: 229
estado: Aceptada
fecha: 2026-09-09
modifica: [226]
---

# ADR-0229: Los tags pasan a ser obligatorios — corrige #226 — y la duración de una franja se escribe

## Decisión

**Dos cosas.** (a) **`TABLE_NEEDS_TAG`**: una mesa necesita al menos un tag, igual que un sistema y una plataforma. Revierte la parte de #226 que los dejaba opcionales. El wizard marca el campo como a los otros dos. (b) **La duración de cada franja se escribe** en un campo de tiempo en vez de elegirse de una lista cerrada de horas redondas

## Por qué

**(a)** El usuario notó que el campo de tags era el único que no se marcaba, que es #226 funcionando: ahí había elegido dejarlos opcionales, con el argumento de que obligar produce tags de relleno que un admin después fusiona (#59). Se le señaló la contradicción y **eligió revertirla**: una mesa que nadie puede encontrar por tema es el peor de los dos problemas. Queda como corrección explícita y no como olvido. **(b)** El comentario que justificaba la lista cerrada decía que *«dejar escribir 03:47 hace una pregunta que la mesa no puede contestar»*, y estaba mal: **45 minutos y 2 h 45 son sesiones reales** —un one-shot corto, una sesión que arranca tarde— y una lista de números redondos solo sirve a las mesas que casualmente son redondas. Un `input[type=time]` las cubre todas y no agrega nada que haya que mantener. Verificado con los dos valores que la lista no tenía: el rango cierra en 20:45 y en 22:45.
