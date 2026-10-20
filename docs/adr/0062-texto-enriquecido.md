---
adr: 62
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0062: Texto enriquecido

## Decisión

**`LONGTEXT` en las tablas que ya existen: en la solicitud (`table_registrations`) va el texto con que el jugador se postula, y en la mesa van sus requisitos. El archivo pasa a ser opcional** — nunca es una fila de `files`

## Por qué

Corrige la versión anterior de esta decisión, que dejaba el destino sin nombrar. El texto enriquecido no es un archivo: no ocupa disco, no se descarga y no participa de la cuota (#61). Y no reemplaza al adjunto, lo vuelve **opcional**: según el master, una postulación puede pedir archivos, solo texto explicando por qué debería entrar, o ambas cosas. Por eso las dos vías conviven en la misma solicitud. Como el texto se renderiza en el navegador, **se sanitiza al guardar y al servir**: es la superficie de XSS más directa del sistema.
