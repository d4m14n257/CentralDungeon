---
adr: 287
estado: Aceptada
fecha: 2026-10-01
modificada-por: [288]
modifica: [286]
---

# ADR-0287: Las decisiones sobre una entidad van en su aviso de estado, arriba de las pestañas, nunca en una pestaña o sección aparte

> Corrige #286; regla fija 15 de la skill `diseno`

## Decisión

`StatusNotice` (`components/`), sobre el `Alert` del inventario: título que nombra la situación, una frase y los botones con texto en una sola fila, debajo de la cabecera y visible desde todas las pestañas. En `/admin/tables/:id` es `AdminTableStatusNotice`, con un título y una frase por cada uno de los once estados —también los que no tienen nada que decidir, como un borrador o una mesa terminada— y **todas** las decisiones juntas: aprobar, pedir cambios y cancelar (`AdminTableDecisions`), asignar, dar de baja, pausar y reanudar (`AdminTableActions` con `presentation="notice"`, que en la fila de la lista sigue dibujándose con íconos). La pestaña «Estado» del admin pasó a ser **«Historial»** (`/admin/tables/:id/history`) y solo muestra el historial. La ayuda dejó de mandar a la pestaña «Estado»

## Por qué

Lo dijo el usuario: la cancelación en una pestaña de estados «se ve horrible […] no un apartado para un único botón», y prefirió el formato del aviso «Esta mesa espera tu revisión»; pidió guardarlo como regla para lo que venga. **Por qué todas las decisiones y no solo la cancelación**: dejar asignar, pausar o reanudar solos en la pestaña habría repetido el mismo problema con otro botón. **Por qué el aviso aparece en todos los estados**: si se mostrara solo cuando hay algo que decidir, el lector no aprendería dónde mirar y el bloque aparecería y desaparecería según el caso. **Lo que no cambia**: editar sigue siendo la acción principal de la cabecera (#280), y en la lista de mesas las acciones de fila siguen siendo íconos (#272). La pestaña «Estado» del master no se tocó: ahí las transiciones son del master y conviven con el pedido de pausa; si se le aplica la regla, es otra decisión
