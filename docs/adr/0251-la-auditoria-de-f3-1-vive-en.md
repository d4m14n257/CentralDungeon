---
adr: 251
estado: Aceptada
fecha: 2026-09-14
---

# ADR-0251: La auditoría de F3.1 vive en dos tablas propias por entidad, no en approvalrequests ni esperando auditlogs

## Decisión

`user_role_changes` y `user_status_changes`, calcadas de `table_status_changes`: fila inmutable —sin `updated_at`, sin `deleted_at`, sin un solo setter—, id UUID v7 en `@PrePersist`, constructor público con todo y **justificación obligatoria** (`NOT NULL` en la tabla, `@NotBlank` en el request). Se leen por `GET /api/v1/admin/users/{id}/history`, que las normaliza en un `UserAdminChangeResponse` común.

## Por qué

`roles-y-alcance.md` §4, F3.1 (skill `modelo-datos`) dejó la pregunta abierta a propósito y recomendaba **no** inventar una tabla que F6 va a reemplazar, usando `approval_requests` si ya existiera — pero F3.2 va después de F3.1, así que no existe. Las otras dos salidas eran peores: **invertir el orden** obliga a registrar un otorgamiento —que no se aprueba, se hace— en un mecanismo de aprobación, y **diferir a F6** deja el bloqueo y el cambio de rango sin responsable durante tres fases enteras. La tabla propia no es una invención: es el precedente que el proyecto ya usa para el cambio de estado con justificación, y F6 no la reemplaza sino que la complementa — `audit_logs` cubre lo transversal con diff de columnas (#92), estas dos son el historial específico de una cuenta. **El endpoint de lectura entra con ellas y no después**: sin él las dos tablas son escritura pura y nacen huérfanas, que es exactamente la clase de hueco que F1.7 relevó y §7 de la fase advierte.
