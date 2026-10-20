---
adr: 288
estado: Aceptada
fecha: 2026-10-06
modifica: [287]
---

# ADR-0288: En un aviso de estado, las acciones destructivas van aparte, en el extremo derecho, cuando hay otros botones

> Precisa #287; ampliación de la regla fija 15 de la skill `diseno`

## Decisión

`StatusNotice` gana `destructiveActions`, que se dibuja con `ml-auto` en la misma fila (la fila ocupa todo el ancho: `AlertDescription` es una grilla que ajusta cada hijo a su contenido, y sin eso el margen no empujaba). En `/admin/tables/:id`, «Cancelar mesa» y «Dar de baja» van ahí; `AdminTableDecisions` y `AdminTableActions` aceptan `only: 'safe' | 'destructive'` para dibujar cada mitad en su lugar. **Si la destructiva es el único botón** —una mesa abierta solo tiene «Cancelar mesa»— va en su lugar normal, a la izquierda. En un teléfono baja de línea y sigue a la derecha

## Por qué

Lo pidió el usuario como mejora visual: «el botón cancelar mesa debería estar a la derecha del mensaje si tiene más botones ya que es una cancelación y puede llegar a haber un miss click», sabiendo que igual pide confirmación. La confirmación (#283) frena el error; la distancia evita llegar a él. La condición «si tiene más botones» es suya y se respetó: un botón solo en el extremo de un aviso ancho se ve perdido, y no hay nada de lo que alejarlo
