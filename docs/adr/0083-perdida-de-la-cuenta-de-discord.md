---
adr: 83
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0083: Pérdida de la cuenta de Discord

## Decisión

**No hay recuperación automática ni self-service. Lo único posible es que el owner migre los datos del usuario viejo a una cuenta nueva**, como operación de administración del sistema

## Por qué

Cierra **M6**. Perder la cuenta de Discord, o el acceso a ella, está fuera del alcance de CentralDungeon: no hay contraseña propia, email ni ningún otro dato con el que reconocer a alguien. La migración manual cubre el caso real —una persona conocida de la comunidad que vuelve con otra cuenta— sin construir un sistema de identidad paralelo. Queda auditada como cualquier acción del owner (#66, #67).
