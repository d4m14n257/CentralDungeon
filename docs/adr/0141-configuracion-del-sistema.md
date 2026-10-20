---
adr: 141
estado: Aceptada
fecha: 2026-08-29
modificada-por: [263]
---

# ADR-0141: Configuración del sistema

## Decisión

**Tabla `system_settings` clave-valor con tres categorías** —parámetros de negocio, límites y cuotas, y textos—, editable por admins en `/admin/settings` y **auditada como cualquier otra entidad** (#92). **Ningún secreto vive acá**: el HMAC de #94 y las credenciales siguen en el entorno

## Por qué

No existía ni la ruta ni la tabla, y hay una lista concreta de valores hoy incrustados en el código que la comunidad va a querer mover sin un despliegue: karma inicial, ventana de decaimiento, caducidad de visibilidad (#44), timeout de reserva de la bandeja (#100), tope por archivo, cupo máximo, ventana del feedback (#94) y la justificación por defecto del rechazo automático (#34). Clave-valor y no una columna por ajuste porque agregar un ajuste no debe ser un `ALTER TABLE`, que es el mismo razonamiento de #10 con los `ENUM`. **La regla dura 3 no se rompe**: el almacenamiento es genérico pero el `SettingsService` expone accesores tipados y el endpoint devuelve su `record` con nombre propio — lo dinámico nunca cruza la frontera HTTP. **Dos avisos que van con la función**: cambiar la ventana de decaimiento del karma altera el cálculo **retroactivamente** en el siguiente recálculo (#97), y cambiar la caducidad de visibilidad cambia **quién puede ver a quién** de inmediato (#44). Ninguno de los dos es un ajuste cosmético y la pantalla tiene que decirlo.
