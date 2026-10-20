---
adr: 78
estado: Aceptada
fecha: 2026-08-29
modificada-por: [255]
---

# ADR-0078: Referencia de la tabla de aprobaciones

## Decisión

**Polimórfica: `entity_type` + `entity_id`, sin FK real**

## Por qué

Cierra **M13**. Agregar un flujo nuevo es agregar un valor al enum, sin `ALTER TABLE`. A cambio, la integridad **no** la puede garantizar la base: el service tiene que validar que la entidad referenciada exista antes de crear la fila, y hace falta una verificación periódica de referencias huérfanas — es la clase de problema que el proyecto ya sufrió (`table_files` apuntando a archivos borrados). En JPA no se mapea como `@ManyToOne`: es un id suelto que el service resuelve según el tipo.
