---
adr: 139
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0139: Cómo se especifican los cuatro estados obligatorios

## Decisión

**Catálogo por arquetipo, no por pantalla.** Cuatro arquetipos —listado, detalle, formulario, dashboard— por cuatro estados, y **toda pantalla hereda los de su arquetipo**. Lo único que cada pantalla define por su cuenta es el **texto del vacío y qué acción ofrece**, en una tabla propia

## Por qué

skill `diseno` §5 exige los cuatro estados en toda pantalla que lea datos, y `plan-desarrollo.md` §6 los pone como condición de cierre de fase — pero las 20 pantallas dibujadas estaban **todas en su estado feliz**, sin un solo skeleton ni estado vacío. Dibujar 20 × 4 = 80 variantes se descartó porque el skeleton, el error y el 403 son **idénticos** en los ocho listados: serían setenta y pico de renders repetidos que además envejecen en paralelo y terminan divergiendo. El arquetipo captura lo que se repite y la tabla de textos captura lo único que no. También se descartó hacerlo solo para las pantallas de E1: alineado con la definición de terminado, pero deja el patrón a medio definir y cada etapa lo reinventa distinto. **Un hallazgo del catálogo**: dos estados vacíos —`/master` y `/admin/queue`— son **buenas noticias** ("nada espera tu respuesta"), y tienen que leerse como tales y no como una pantalla rota, que es como se ven si se reusa el vacío genérico sin pensarlo.
