---
adr: 274
estado: Aceptada
fecha: 2026-09-28
---

# ADR-0274: Una sola skill de arquitectura

> Precisa lo que F4.0 decidió sobre las skills

## Decisión

`arquitectura-backend`, `arquitectura-frontend`, `modelo-datos`, `tests-java`, `nuevo-endpoint-java`, `nuevo-componente-react` y `er-diagram-sync` se funden en **`arquitectura`**: backend §2 y frontend §3 con su numeración de siempre; el modelo de datos en §4, con los números viejos bajo el prefijo `4.` (el §5 de reglas de negocio es §4.5); **testing en §5** (el viejo §2.7 es §5.1, el §3.4 es §5.2, más el cómo de `tests-java` y un §5.3 de e2e); **documentación del código en §6** (Javadoc §6.1, que era §2.8, y JSDoc §6.2); y **los procedimientos en §7** (nuevo endpoint, nuevo componente o pantalla, cambio de `@Entity`). `roles-y-alcance.md` conserva su numeración interna. Quedan tres skills: `arquitectura`, `diseno` (#273) y `entorno-local`. Las ~330 citas del código y los documentos se reescribieron con un mapa explícito, **salvo las dos que están en comentarios de migraciones aplicadas**, que no se tocan porque el checksum de Flyway cubre el archivo entero

## Por qué

Pedido del usuario: la arquitectura es una sola cosa, y el modelo de datos es parte de ella; el testing y la documentación con Javadoc/JSDoc no son skills aparte sino cómo se escribe el código. Repartida en siete, cada tarea obligaba a cargar tres o cuatro skills que se citaban entre sí —el procedimiento de un endpoint no servía sin las reglas de capas, ni el de un test sin la regla que probaba— y las reglas del Javadoc vivían a la vez en `CLAUDE.md`, en el §2.8 y en el §3.3. Los números viejos se conservaron **porque el código ya los cita**: renumerar desde cero habría dejado cientos de citas apuntando a otra sección sin que nada fallara. `entorno-local` queda aparte porque no es arquitectura: es cómo se porta la máquina
