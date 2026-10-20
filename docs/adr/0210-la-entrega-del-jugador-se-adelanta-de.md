---
adr: 210
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0210: La entrega del jugador se adelanta de F2 a F1.5

## Decisión

**`task_submissions` y `submission_files` se mapean y el jugador entrega en F1.5**, con texto y archivos. el documento de F1 (§5, hoy en git) y `plan-desarrollo.md` se corrigen en el mismo commit

## Por qué

El documento de fase daba F1.5 como "el master publica y **lee** las entregas". Sin nadie que pueda entregar, el panel de entregas muestra a todos como faltantes siempre, no hay flujo e2e que probar y la regla que más importa del subsistema —las entregas se acumulan (#76)— queda escrita y sin ejercitar. El costo de adelantarlo es bajo: los archivos ya existen desde F1.4 y entregar es un `POST` que inserta. Lo que **no** se adelanta es `/my/files` ni el archivo de personaje en la postulación, que siguen en F2
