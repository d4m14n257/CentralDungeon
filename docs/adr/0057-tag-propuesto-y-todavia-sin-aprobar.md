---
adr: 57
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0057: Tag propuesto y todavía sin aprobar

## Decisión

**La mesa se publica igual y conserva el tag, pero el tag no es visible para los jugadores ni se puede filtrar por él mientras esté en `Created`.** Al aceptarse pasa a mostrarse y a filtrar; si se rechaza, ni aparece ni filtra nunca

## Por qué

Cierra **M20.1**. Desacopla el ritmo del master del ritmo del admin: proponer un tag nuevo no bloquea publicar la mesa, y un tag sin revisar tampoco ensucia la búsqueda de los jugadores con valores que quizá no sobrevivan. El costo es que el master ve su mesa con un tag que los demás no ven, así que la interfaz tiene que dejar claro que está pendiente de aprobación.
