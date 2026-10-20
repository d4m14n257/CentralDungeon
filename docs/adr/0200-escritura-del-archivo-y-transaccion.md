---
adr: 200
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0200: Escritura del archivo y transacción

## Decisión

**Se escribe a un área de staging y se confirma en `beforeCommit`; el rollback borra lo que haya quedado**

## Por qué

Cierra **M26.2**, que seguía abierta. Base y disco no comparten commit: escribir directo deja un huérfano cada vez que la transacción que iba a registrar el archivo falla — el TODO sin resolver del legacy (M21.6). El movimiento va en `beforeCommit` y no después del commit **a propósito**: si el disco no puede recibir el archivo, la transacción se cae y la fila nunca se escribe. Al revés —commit primero, mover después— esa falla no se puede deshacer y deja una fila apuntando a nada
