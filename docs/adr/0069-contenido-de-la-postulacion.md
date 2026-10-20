---
adr: 69
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0069: Contenido de la postulación

## Decisión

**Texto enriquecido y archivos, ambos opcionales por separado; el master define qué exige su mesa.** La postulación **no** se modela como una petición más: conserva su tabla

## Por qué

Responde la pregunta 1 del diseño de #63. Un master puede pedir solo archivos, solo una explicación de por qué debería entrar, o las dos cosas — así que ninguna de las dos vías puede ser obligatoria en el modelo. Y mantener `table_registrations` como está evita mezclar el flujo de admisión (#28) con el de peticiones, que tienen reglas distintas.
