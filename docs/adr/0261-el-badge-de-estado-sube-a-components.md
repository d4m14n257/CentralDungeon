---
adr: 261
estado: Aceptada
fecha: 2026-09-20
modificada-por: [273]
---

# ADR-0261: El badge de estado sube a components/; el diálogo con motivo obligatorio no

> Las dos mitades de skill `arquitectura` §3.1.2, aplicadas al mismo relevamiento

## Decisión

Subir un `StatusBadge` sin dominio a `components/`, que reciba **la etiqueta ya traducida y el par de clases ya resueltas**; cada feature se queda con su `Record<Estado, { badge, dot }>` y su `t()`. **No** subir un «diálogo con motivo»: los siete que existen ya comparten `FormDialog`, que es lo que tenían que compartir. Inventario de los dos grupos en skill `diseno` §5

## Por qué

Salió de abrir el inventario completo de compuestos: hay **63 en 10 features** y el documento nombraba 15. Con la lista entera a la vista aparecieron dos grupos que se parecían y que la regla resuelve al revés. **El badge estaba escrito diez veces** —nueve en features y una dentro de `NotificationsPage`, que el primer recuento no vio por buscar solo donde los componentes deberían estar— y ocho de ellas eran el mismo texto: el mismo `<span>`, el mismo punto de 1.5, el mismo orden de hijos, y difieren **solo** en el namespace de i18n, el prefijo de la clave y el mapa a tokens. §3.1.2 pone el umbral en dos usos y explica por qué es más bajo que el del backend — «acá la alternativa a subir no es un poco de duplicación: es un import prohibido» —, así que diez no era un caso de borde. **El diálogo con motivo son siete en cinco features** y ahí el tercer punto de la misma regla dice lo contrario: «no sube lo que solo se parece; dos formularios comparten `FormDialog`, que es el envoltorio». Lo que queda distinto en cada uno es la mutación, el éxito y los campos alrededor del motivo — `RoleChangeDialog` elige un rol, `ApplyToTableDialog` tiene dos pasos y adjuntos, `BlockPlayerDialog` cambia de endpoint según si el lector es `Primary` —, y eso es lógica de feature. **Y la causa de fondo es el inventario, no los componentes**: la tabla curada de §5 nombraba dos de los diez badges, así que cada fase nueva leía «un badge es cosa de cada feature» y escribía el siguiente. Un inventario incompleto no es desprolijidad: es el mecanismo por el que el duplicado se reproduce. Por eso #261 entrega el inventario completo además de las dos decisiones. `BlockPlayerDialog` ya había dejado la pregunta por escrito en su JSDoc — «no es `JustifiedTableActionDialog`, que es la forma idéntica una feature más allá» — y lo que faltaba era el recuento que la contesta
