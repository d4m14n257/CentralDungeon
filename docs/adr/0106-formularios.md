---
adr: 106
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0106: Formularios

## Decisión

**El formulario es un componente puro, desacoplado de su contenedor.** Recibe valores iniciales y un `onSubmit`, y no sabe si vive en un modal, un panel lateral o una página. El modal es un envoltorio aparte

## Por qué

Es la separación que ya tenía el proyecto viejo entre `forms/` y los modales que los alojaban, y es correcta: el mismo formulario de mesa sirve para crear en un modal y para editar en una página sin tocarlo. La diferencia es dónde vive: en vez de una carpeta global `forms/` que mezcla dominios, cada formulario va en `features/<dominio>/components/` junto a lo que usa.
