---
adr: 208
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0208: @Transactional en un método @Scheduled que delega

## Decisión

**La anotación va en el método que llama el scheduler, no solo en el que hace el trabajo**

## Por qué

Encontrado al revisar F1.4 antes de cerrarla: `purgeUnusedFiles()` llamaba a `markUnusedFiles()`, que era el transaccional. Una llamada de un método al otro dentro de la misma clase es **autoinvocación** y no pasa por el proxy, así que la transacción nunca se abría. Sin transacción, la consulta corre en la suya de lectura, las entidades vuelven desprendidas, y marcarlas no escribe nada — el job informaba un conteo y no cambiaba una fila, sin lanzar ni loguear nada. **Un unitario no lo puede ver** porque mockea el repositorio: la cobertura de un job que muta filas tiene que incluir un test de integración que llame al método que llama el scheduler y después consulte la base
