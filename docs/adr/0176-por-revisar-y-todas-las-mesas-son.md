---
adr: 176
estado: Aceptada
fecha: 2026-09-02
modificada-por: [286]
---

# ADR-0176: "Por revisar" y "todas las mesas" son dos pantallas distintas

## Decisión

**`/admin/queue` es lo que pide una acción**: mesas esperando aprobación, comentarios en moderación, pausas pedidas, feedback. Es compartida, con reserva, y un ítem desaparece cuando otro admin lo toma (#100). **`/admin/tables` es el listado de administración**: **todas** las mesas, en cualquier estado, con filtros y buscador, para ver cómo va cada una — sin reserva y sin la idea de "pendiente". Hoy `/admin/tables` filtra por `Unassigned`/`Preparation`/`ChangesRequested` y ofrece Aprobar y Pedir cambios: eso es la bandeja haciendo escala en la pantalla equivocada, porque `/admin/queue` todavía no existe (llega con `approval_requests`, E2 sub-rebanada 2). **Al existir la bandeja, las acciones de revisión se mudan ahí** y `/admin/tables` pasa a listar todo — lo que implica que `DEFAULT_ADMIN_REVIEW_STATUSES` deja de ser el default de `listForAdmin`: el default pasa a ser "todos menos `Deleted`"

## Por qué

Son dos preguntas distintas y confundirlas construye dos colas o ninguna administración. "¿Qué tengo que resolver?" se contesta con una lista corta, que se vacía, compartida y con reserva para que dos admins no trabajen el mismo ítem. "¿Cómo va la mesa de Fulano?" se contesta con un listado completo que nunca se vacía y donde lo que importa es encontrar: filtros y buscador. Un admin **administra**, y eso incluye mirar mesas que no requieren nada de él. Tampoco es un problema de permisos —el backend ya devuelve cualquier estado sin filtrar por pertenencia— sino de qué muestra cada pantalla: la restricción de hoy es del listado, no de la autorización
