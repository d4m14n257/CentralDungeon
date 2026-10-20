---
adr: 88
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0088: Qué de Discord necesita un bot

## Decisión

**Con solo OAuth2 (v1) se puede: identificar al usuario y verificar si es miembro del servidor. Necesitan bot: detectar baneos, crear y borrar canales por mesa, y leer la lista de miembros.** Enlazar a un canal que ya existe **no** necesita bot: es un link

## Por qué

Aclara #86 y acota la integración futura. La detección automática de baneos, los canales de voz por mesa y el abrir/cerrar canales según el estado de la mesa son **la misma pieza de trabajo**: un bot con permisos sobre el servidor. Conviene hacerlas juntas y no de a una. Lo único que se puede adelantar sin bot es el enlace directo a un canal existente.
