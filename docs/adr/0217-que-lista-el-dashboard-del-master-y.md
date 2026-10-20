---
adr: 217
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0217: Qué lista el dashboard del master, y cómo se resuelve «entregas sin revisar»

## Decisión

**Cinco tipos de ítem** —`CandidatesWaiting`, `OverdueTaskMissing`, `SessionToRecord`, `ChangesRequested`, `ReadyToStart`—, **ordenados por tiempo de espera**, con el backend mandando código y parámetros y el frontend armando la frase (#197). «Entregas sin revisar» **no se construye**: el ítem accionable es **la petición vencida con gente que no entregó**

## Por qué

El preview de diseño (`design/out/screen-master-dashboard.html`) dibuja «2 entregas sin revisar», y eso **no se puede computar**: no existe ningún marcador de «revisada» en el modelo —`SubmissionStatus` tiene `Pending` y `Submitted`, nada más— y agregarle uno sería introducir un concepto de negocio que ningún documento pidió, además de rozar #76, que dice que el sistema no juzga las entregas. Lo que sí pide un documento es #70: el incumplimiento tiene que quedar **visible para el master**, sin bloquear ni expulsar. Así que el ítem es «tal petición venció y N personas no entregaron», que es accionable, no necesita columna nueva ni migración, y cumple #70 en vez de inventar otra cosa. Una petición que todo el mundo contestó no genera ítem: que pase la fecha no es, por sí solo, trabajo. El conteo es destinatarios menos personas que entregaron —**personas**, no filas, porque las entregas se acumulan (#76)—. `PauseRequested` **no entra** aunque el preview lo dibuje: es F3 y ningún endpoint lo produce todavía; queda anotado, no inventado. El orden por tiempo y no por cantidad es #136 literal: una persona esperando nueve días pesa más que tres esperando dos horas.
