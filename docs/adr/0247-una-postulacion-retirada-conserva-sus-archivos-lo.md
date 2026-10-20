---
adr: 247
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0247: Una postulación retirada conserva sus archivos; lo que muere es el uso, no la fila

> Matiza #175 y #232

## Decisión

`registration_files` **no se arrastra** al retirar una postulación ni al borrar una mesa: las filas quedan como estaban. Lo que sí las trata como muertas son las lecturas: la cuarta consulta de usos de #232 exige una postulación **viva**, así que un archivo cuyo único vínculo era una postulación retirada reporta «sin usar» en `/my/files` y vuelve a estar al alcance de la purga (#75). El cajón `PlayerApplication` tampoco se revoca, porque nunca se revoca ninguno (#233).

## Por qué

#175 hizo que borrar una mesa arrastre `masters` y `table_registrations` con la misma marca de tiempo, y lo natural era extender la cascada un nivel más. **No se extiende, y la razón es que las dos tablas contestan preguntas distintas.** Una fila de `table_registrations` es una relación entre una persona y una mesa, y esa relación se deshizo de verdad cuando la persona se retiró. Una fila de `registration_files` es un hecho: *esta hoja fue adjuntada a esta postulación*. Retirarse no lo deshace, lo mismo que despegar un archivo de una mesa no hace que nunca haya sido material de esa mesa — que es exactamente la distinción que #232 puso como base de todo el diseño de archivos. Marcar la fila no agregaría información y borraría la única traza de qué se había mandado. **Lo que no puede pasar es que el registro se confunda con un vínculo vivo**, y ahí es donde se paga: la consulta de usos filtra por el estado de la postulación, no por el de la fila puente, y el job de retención hereda ese mismo filtro. Sin eso, retirar una postulación dejaría el archivo inmunizado contra la purga para siempre, que es la clase de fuga silenciosa que #75 existe para evitar. **Y no hay contradicción con #25**: la postulación `Deleted` sigue sin aparecer en ninguna lectura — lo que sobrevive no es la postulación, es el papel que llevaba adentro.
