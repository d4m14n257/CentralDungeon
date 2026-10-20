---
adr: 197
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0197: Ningún texto que lee una persona se escribe ni se guarda en el backend

## Decisión

Las notificaciones guardan **tipo + parámetros** (`NotificationParams`, columna `params`) y no una frase; los `ApiException` llevan `errorCode` + `errorParams` y su `message` pasa a ser inglés para un log; el rechazo automático de #34 guarda el código `TABLE_FULL` en vez de «Mesa llena». El frontend arma la oración

## Por qué

Es lo que hace posible #198. Una fila de `notifications` se escribe una vez y se lee durante meses, y en el medio la persona puede cambiar el idioma de la aplicación: una frase guardada queda **congelada en el idioma del día en que se emitió**, y ninguna cantidad de traducción posterior la arregla. Lo mismo con el `detail` de un 409: mostrarlo tal cual era mostrar español a alguien que eligió inglés. **Deroga la excepción de #188** («se escribe en español porque se muestra verbatim») y la de #34 para el motivo automático — el de un master **sigue** verbatim, porque eso lo escribió una persona y traducirlo sería ponerle palabras en la boca. `errorParams` es un `Map` y no un record con nombre, única excepción a la regla dura 3: no es un cuerpo de respuesta sino un *extension member* de RFC 9457, que es el mecanismo que el estándar define para esto, y un record por código de error sería un tipo por mensaje sin reutilizar ninguno
