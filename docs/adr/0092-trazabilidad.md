---
adr: 92
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0092: Trazabilidad

## Decisión

**`audit_logs` registra qué entidad se modificó, su id, quién, cuándo, y el antes/después **solo de las columnas que cambiaron**, no la fila completa. Hay una vista exclusiva del `Owner` para consultarlo**

## Por qué

Precisa el alcance de la auditoría: no es analítica de uso —nadie registra clics— sino trazabilidad de **cambios de datos**, para poder reconstruir qué hizo cada persona en el sistema. Guardar solo el diff mantiene la tabla manejable y hace legible cada fila. **La excepción sigue en pie: `comments` y `comment_drafts` no se auditan** (#43); auditarlos guardaría autor y contenido juntos y anularía el anonimato. Es un hueco deliberado, no un olvido.
