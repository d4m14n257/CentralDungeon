---
adr: 248
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0248: El perfil entra en F2 con la mitad de arriba, y el bloque de comentarios no se dibuja vacío

## Decisión

`/player/profile` y `/player/users/:id` se construyen en F2 con nombre, país, roles y la asistencia agregada de #137. **La respuesta no lleva `karma` ni `comments`** — ni siquiera en `null` — y la pantalla **no dibuja** la sección «Comentarios recibidos». Las dos llegan en **F5** con `KarmaService` y `comments` (corrido por #250).

## Por qué

El wireframe de `diseno` §4 dibuja las dos mitades y la de abajo es F4, así que la pregunta era si conviene esperar. No conviene, y el motivo no es la pantalla: son **las cinco reglas de visibilidad de perfiles** de skill `arquitectura` §4.5 (#41, #44, #45, #47), que están escritas desde hace un año y no tienen **una sola línea implementada**. F4 las va a necesitar funcionando para decidir quién puede comentar a quién, y llegar a F4 con la regla de privacidad más delicada del sistema sin estrenar sería construir el karma sobre una puerta que nadie abrió nunca. Con perfil en F2 llegan probadas, con su matriz y su ventana temporal ejercitada por gente real. **Por qué el bloque vacío no se dibuja**: skill `diseno` §5 pide que el estado vacío se lea como información y no como rotura, y una sección titulada «Comentarios recibidos» sin nada abajo no dice «todavía nadie te comentó» —que sería honesto— sino «esto debería tener algo». Es la misma trampa que el dashboard del master esquivó en F1.6 poniendo el vacío como buena noticia. **Y por qué el campo no viaja en `null`**: un DTO que promete `karma` le enseña al frontend a preguntarlo, y el primer componente que lo lea va a mostrar un hueco donde va un número.
