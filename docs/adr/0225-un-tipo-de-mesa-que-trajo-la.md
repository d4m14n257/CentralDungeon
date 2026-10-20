---
adr: 225
estado: Aceptada
fecha: 2026-09-09
---

# ADR-0225: Un tipo de mesa que trajo la aplicación se traduce; uno que escribió una persona, no

## Decisión

**`table_types` gana una columna `code` nullable y única.** Las dos filas que siembra `V2__seed.sql` pasan a `PUBLIC` y `FIRST_CLASS`; la API las manda junto al `name`, y el frontend arma la etiqueta con `t('tableTypes.<CODE>.name')` en el idioma de quien lee — nombre y descripción. **`code IS NULL` es el diseño entero**: significa «esto lo nombró una persona», y entonces el `name` se muestra tal cual, igual que en `systems`, `tags` y `platforms`. El `name` de la API queda además como respaldo, para que un código que llegue antes que su traducción se lea como palabras y no como `tableTypes.ALGO.name`. **La regla dura 18 no se toca**: el backend sigue sin escribir una sola frase que lea una persona

## Por qué

Lo reportó el usuario: el tipo de mesa se veía «Public» y «First class» con la interfaz en español. Su propuesta era que **el backend respondiera en el idioma del cliente** —`Accept-Language` y `MessageSource`—, y creía recordar un intento así en alguno de los repos viejos. **Se revisaron los dos y no hay nada**: ni `MessageSource`, ni `LocaleResolver`, ni un solo `messages*.properties`. Lo que sí había era la respuesta ya tomada: **`V4__notification_params.sql` resolvió exactamente esta clase de problema**, convirtiendo «Mesa llena» en el código `TABLE_FULL`, y dejó escrito el criterio en la migración — *el texto del master es suyo y queda verbatim, pero «Mesa llena» lo escribió la aplicación y tiene que seguir el idioma de quien lee*. «Public» y «First class» son ese mismo caso. El usuario eligió el código sobre el i18n en el backend con eso a la vista. **Los otros catálogos no tienen el problema y no se tocan**: `D&D 5e`, `Discord`, `Roll20`, `Presencial` son nombres propios, no frases — traducirlos sería el error opuesto. Y el i18n del backend tampoco habría resuelto los tipos que agregue un admin: esos siguen siendo verbatim de cualquier forma, así que el respaldo hacía falta igual. De paso se corrigió `MasterTableEditPage`, que preseleccionaba el tipo **comparando la etiqueta** (`type.name === table.tableTypeName`): eso se rompía en el momento en que la etiqueta empezaba a traducirse, así que ahora compara por `code` cuando lo hay.
