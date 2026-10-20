---
adr: 44
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0044: Caducidad de la visibilidad de perfiles

## Decisión

**Dos semanas contadas desde que la mesa entra en `Finished` o en `Canceled`.** Mientras la mesa esté en `Pause` el reloj no corre: la visibilidad sigue abierta indefinidamente. Pasado el plazo se deja de ver el perfil de masters y jugadores con los que se coincidió, aunque el registro de con quién se jugó se conserva

## Por qué

Cierra **M14.1**. Acota la ventana en que el karma sirve para decidir (postularse, aceptar a un candidato) sin convertir la plataforma en un directorio permanente de personas. El ancla es el cierre de la mesa porque es cuando la relación entre esas personas termina de verdad; una mesa pausada no terminó, solo está detenida, y sus integrantes siguen siendo compañeros de mesa. El historial se guarda —hace falta para el karma y la moderación— pero deja de ser visible.
