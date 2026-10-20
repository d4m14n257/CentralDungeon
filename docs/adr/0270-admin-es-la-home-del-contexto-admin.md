---
adr: 270
estado: Aceptada
fecha: 2026-09-28
---

# ADR-0270: /admin es la home del contexto Admin: una bienvenida, sin métricas

> Corrige la home que F3.3 le había dado a la bandeja

## Decisión

`/admin` pasa a ser una ruta índice con título y una línea de bienvenida; **es donde cae un admin al entrar** (`homePathFor('admin')`) y el primer ítem de `AdminSectionNav` («Inicio», con `end` para que no quede subrayado en todas las pantallas). La bandeja queda segunda, primera de las pantallas de trabajo

## Por qué

El prefijo era una página huérfana: sin índice, sin redirect y sin 404 (`fase-4-revision.md` §2.2). El usuario no sabe todavía qué métricas le sirven a un admin para abrir, y una página de números que nadie lee es peor que un saludo; **cuando esa decisión exista, reemplaza el cuerpo** — la ruta y el lugar en la nav ya son de la home. Que la home sea la dirección del propio contexto (`/admin`, como `/master` y `/player`) es lo que hace que el prefijo deje de ser un cascarón
