---
adr: 206
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0206: Quién puede leer lo que una mesa comparte

## Decisión

**Cualquiera que pueda ver la mesa, sin exigir pertenencia**

## Por qué

Lo mismo que ya viaja dentro de `GameTableDetailResponse`: exigir pertenencia en la descarga listaría el archivo y después se negaría a entregarlo, que es lo peor de los dos mundos. Y es el caso para el que los archivos existen — lo que un master adjunta a su mesa abierta es la hoja y las reglas de la casa, y quien más necesita leerlas es alguien **todavía decidiendo si postularse** (#60 uso 1). Lo privado no llega nunca acá. **Al construir el veto (F3) hay que excluir al vetado también en esta lectura**: hoy `TableRegistrationStatus` ni siquiera tiene `Blocked`, así que no hay a quién excluir (#29, #39)
