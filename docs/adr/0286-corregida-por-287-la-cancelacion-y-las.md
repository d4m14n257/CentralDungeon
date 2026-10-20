---
adr: 286
estado: Aceptada
fecha: 2026-10-01
modificada-por: [287]
modifica: [176]
---

# ADR-0286: (Corregida por #287: la cancelación y las demás acciones ya no están en una pestaña Estado, sino en el aviso de estado arriba de las pestañas.) El admin decide sobre una mesa desde su propia pestaña Estado: aprobarla o pedir cambios con el motivo mientras está en revisión, y cancelarla con el motivo cuando no tiene arreglo — precisa #176, que había dejado la revisión solo en la bandeja

## Decisión

`AdminTableDecisions`, en dos lugares de `/admin/tables/:id`: **la revisión, en un aviso arriba de las pestañas** («Esta mesa espera tu revisión»), visible desde todas —primero estuvo solo en la pestaña Estado, la última, y el usuario no la encontró porque al abrir la mesa se cae en Detalles—; **la cancelación, en la pestaña Estado**, junto a las acciones que ya estaban (asignar, dar de baja, pausar, reanudar). **Aprobar** (confirmado con lo que sigue), **Pedir cambios** (diálogo con motivo obligatorio, que el master lee en el historial) y **Cancelar mesa** (motivo obligatorio, destructivo, desde cualquier estado cancelable salvo `Unassigned`, que se da de baja). Botones con texto, no íconos: son las decisiones de la pantalla, no acciones de una fila (#272). **Solo en el detalle, no en la fila de la lista**: decidir pide haber leído la mesa. Un pedido de pausa del master sigue resolviéndose en la bandeja, porque es un `approval_request`, y la pestaña lo dice. Aprobar y pedir cambios ahora refrescan también el detalle y el historial de la mesa

## Por qué

Lo preguntó el usuario con una mesa en preparación delante: «si entro solo puedo editar […] pero realmente cómo paso el estado, para revisarlo y darle el visto bueno o rechazarla y por qué razón». La pestaña lo mandaba a la bandeja. **#176 sacó la revisión de `/admin/tables` para que dos pantallas no ofrecieran la misma decisión con reglas distintas**; acá las reglas son las mismas —los mismos endpoints y la misma regla de reserva (#100)—, así que una mesa que otro admin tomó en la bandeja se rechaza también desde la mesa, con el mismo mensaje. La bandeja sigue siendo la lista de trabajo; esto es la decisión de quien ya leyó la mesa entera. **«Rechazar» son dos cosas distintas** y las dos están: pedir cambios, que se la devuelve al master para corregir, y cancelar, que la cierra para siempre. La ayuda «Revisar mesas» se reescribió con los dos lugares, y de paso dejó de decir que al entrar a Admin se cae en la bandeja, que dejó de ser cierto con #270
