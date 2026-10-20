---
adr: 66
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0066: Borrado físico de archivos

## Decisión

**Lo hace el owner de la plataforma desde un menú de administración**, y contempla borrar también del almacenamiento en la nube

## Por qué

Cierra **M26.1**. El borrado lógico de la aplicación (#25) nunca toca los bytes; liberar espacio real es una operación de mantenimiento, deliberada y con nombre y apellido, no un efecto secundario de que un usuario apriete "eliminar". **Cuidado con el nombre**: acá *owner* es el dueño de la plataforma (que además es quien la desarrolla), y no tiene nada que ver con `masters.master_type = 'Owner'`, que es el dueño de una mesa. Conviene que en el código no se llamen igual.
