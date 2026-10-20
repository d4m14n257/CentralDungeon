---
adr: 61
estado: Aceptada
fecha: 2026-08-29
modificada-por: [75]
---

# ADR-0061: Cuota de la biblioteca privada DEROGADA por #75

## Decisión

~~Máximo 5 archivos de 2 MB cada uno por usuario~~. Se conserva solo el tope de tamaño **por archivo**

## Por qué

Guardar archivos sin límite convierte la plataforma en almacenamiento gratuito y el costo crece sin techo. El legacy no tenía **ningún** límite: `multer` se configuró sin `limits`, así que no había tope ni de tamaño ni de cantidad. La cuota es por biblioteca privada (uso 4); los archivos atados a una mesa o a una solicitud tienen su propio ciclo de vida y se acotan por otro lado.
