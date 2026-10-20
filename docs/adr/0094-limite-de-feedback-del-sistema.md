---
adr: 94
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0094: Límite de feedback del sistema

## Decisión

**Uno cada 24 horas reales**, con token opaco rotativo: `HMAC(secreto, user_id + hora_UTC)`. Al escribir se guarda el token de la hora actual; para verificar, el service calcula los tokens de las **últimas 24 franjas horarias** y comprueba si alguno existe. Las filas se purgan pasadas 24 h

## Por qué

Resuelve el residuo de **M31** sin abrir un agujero nuevo. Se descartaron dos alternativas peores: el **día calendario** dejaba escribir a las 23:59 y otra vez a las 00:01; y el **token fijo por usuario** con una marca de último envío —lo más directo para una ventana móvil— crea un **seudónimo permanente**: una fila por persona que dura para siempre y permite agrupar todos sus feedbacks a lo largo del tiempo aunque no se sepa quién es. Con el contenido a la vista, eso vuelve mucho más fácil deducir la identidad. El token por franja horaria rota, no persiste y no permite enlazar dos envíos separados por más de un día. La ventana efectiva queda entre 23 h y 24 h, siempre del lado seguro.
