---
adr: 272
estado: Aceptada
fecha: 2026-09-28
---

# ADR-0272: Las acciones de una fila en una tabla son íconos con tooltip, no botones con texto

## Decisión

En las seis tablas de admin, cada acción de `renderActions` es un `IconAction`: ícono de `lucide-react`, **tooltip con el nombre de la acción** al pasar el cursor y el mismo texto como `aria-label`. Las destructivas (borrar, rechazar, bloquear, deshabilitar) llevan `text-destructive`; nada de valores sueltos. El vocabulario de íconos se reusa: aprobar/aceptar `Check`, rechazar `X`, borrar `Trash2`, detalle `Eye`, historial `History`, roles `UserCog`, bloquear `Ban`, desbloquear `LockOpen`, publicar `Globe`, despublicar `EyeOff`, asignar masters `UserPlus`, pausar `Pause`, reanudar `Play`, fusionar `Merge`, separar `Split`, restaurar `RotateCcw`, reservar/liberar `Bookmark`/`BookmarkX`, pedir cambios `MessageSquareWarning`. Sigue valiendo el principio 2: lo que no se puede hacer no aparece. Los botones que no son de una fila —«Crear mesa sin master», «Subir»— siguen con texto

## Por qué

Pedido del usuario: tres o cuatro botones con texto por fila ocupan una columna entera y compiten con los datos; los íconos se escanean mejor y el tooltip dice qué es cada uno. `IconAction` ya existía para esto (skill `diseno` §5, el reemplazo del `ActionButtonDefault` del legacy) y lo usaban las pestañas del master, pero las pantallas de admin de F3 no lo habían tomado. El tooltip no alcanza solo —en un teléfono no hay hover—, por eso el nombre viaja también como `aria-label`, que es lo que ya garantizaba el componente
