---
adr: 16
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0016: Roles y permisos

## Decisión

**Dos capas: rol global + rol por mesa**

## Por qué

Replica la distinción que el schema ya tenía (`users_roles` → Player/Master/Admin; `masters.master_type` → Owner/Master). El rol global se resuelve con `@PreAuthorize`; el rol por mesa en el service, porque depende del recurso concreto. No se diseña un sistema de permisos más granular por ahora.
