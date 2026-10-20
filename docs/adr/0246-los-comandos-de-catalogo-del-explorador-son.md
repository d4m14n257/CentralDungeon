---
adr: 246
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0246: Los comandos de catálogo del explorador son texto libre; el grupo lo resuelve el backend

> Amplía #164 y #239

## Decisión

`/table_system`, `/table_tag` y `/table_platform` se declaran con `examples`, como `/file_name`, y **no** con `values` como `/file_type`. Lo que se escribe viaja tal cual y `GameTableSearchSpecification` lo expande al grupo de sinónimos (#54, #56) antes de tocar la tabla puente. `SearchField` no gana un tercer modo.

## Por qué

#239 introdujo `values` para los campos cuyas opciones son **finitas y ajenas al vocabulario de la gente** —nadie va a tipear un MIME type—, y los catálogos son lo contrario en las dos mitades: son cientos, crecen solos cuando un master propone (#55), y sus valores son exactamente las palabras que la gente usa. Ofrecer la lista obligaría a que el combobox consulte `/api/v1/systems` mientras se tipea: un buscador con debounce adentro del buscador con debounce, en la pieza que #240 acaba de unificar para que hubiera **uno solo**. Y no hace falta para ser descubrible, porque el grupo de sinónimos es justamente lo que perdona escribir mal: quien tipea `DANDD` encuentra lo mismo que quien tipea `D&D`, que es el servicio que una lista de opciones intentaría prestar. **Lo que sí se ofrece son ejemplos reales del seed**, no inventados: los `examples` salen de `V3__catalog_seed.sql`, así que el primero que alguien pruebe devuelve mesas. Si el uso demuestra que hace falta la lista, el tercer modo se agrega entonces — es literalmente el ejemplo de skill `arquitectura` §2.4: se abstrae después de ver la repetición.
