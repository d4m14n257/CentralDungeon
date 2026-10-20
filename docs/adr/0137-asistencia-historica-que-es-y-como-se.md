---
adr: 137
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0137: Asistencia histórica: qué es y cómo se calcula

## Decisión

**Se deriva con `COUNT`, no se cachea.** El denominador son las sesiones **con asistencia registrada** — `Unknown` queda fuera. **No se muestra como razón**: se muestran los tres números (`Present`, `Excused`, `Absent`), porque una razón esconde justo la distinción que importa. Índice cubridor `(user_id, attendance)` para que el agregado sea index-only

## Por qué

Faltaba definirla: apareció en las pantallas como "34 de 36" sin que nadie hubiera dicho qué contaba. Excluir `Unknown` no es un detalle: una mesa de 12 sesiones que arrancó ayer tiene 11 sin registrar, y contarlas haría que **todos** se vean como ausentes crónicos justo cuando un master los está evaluando. No colapsar `Excused` con `Absent` es #98 aplicado un nivel más abajo: si mezclar asistencia con karma vuelve el número inexplicable, mezclar una falta avisada con un plantón vuelve inexplicable la asistencia — el master saca su conclusión mejor con los tres números que con un porcentaje que ya decidió por él. **No se cachea** por el precedente de #11, que eliminó `Players_Table` —un contador mantenido por trigger— y lo reemplazó por un `COUNT` para matar de raíz la clase de inconsistencia; cachear la asistencia repetiría ese error. La PK es `(table_session_id, user_id)` y no sirve para buscar por usuario, pero el FK a `users` ya obliga a un índice sobre `user_id`; el índice cubridor agrega `attendance` para que el conteo no toque las filas. **El riesgo real no es el volumen sino el N+1**: un `COUNT` por perfil es trivial, diez candidatos con asistencia en una lista son diez consultas si se resuelven fila por fila — se agrupan en una sola con `GROUP BY`. Si algún día hiciera falta cachearla, el precedente es el karma (#97): columna de proyección con disparadores de recálculo. Hoy no hace falta y adelantarlo sería optimización sin medición.
