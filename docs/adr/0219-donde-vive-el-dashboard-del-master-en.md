---
adr: 219
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0219: Dónde vive el dashboard del master en el backend

## Decisión

**Paquete propio, `com.centraldungeon.dashboard`**, en vez de `tables/`

## Por qué

Lee de cuatro features a la vez —mesas, sesiones, postulaciones y peticiones— y **nada lee de él**. Ponerlo en `tables/` habría hecho que ese paquete dependiera de `tasks/`, que ya depende de `tables/`: una dependencia circular entre features que hoy son un árbol. Como paquete propio la dirección de todas las dependencias existentes queda intacta y la regla de skill `arquitectura` §2.1 se sigue cumpliendo — es un dominio con su propio endpoint (`/api/v1/master/dashboard`), no una capa técnica. Las sondas por mesa se resuelven con **consultas agrupadas por lista de ids**, el mismo patrón de `CatalogUsageCount`, `FileUsageCount` y `TaskSubmissionCount`: un master con seis mesas cuesta cuatro consultas, no veinticuatro. La única excepción es resolver los destinatarios de una petición vencida, que sí es una consulta por petición: la audiencia se decide en un solo lugar (`TableTaskService.recipientsOf`) y duplicar esa regla en una consulta agregada es exactamente cómo las dos respuestas se desincronizan.
