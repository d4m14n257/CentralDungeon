---
adr: 178
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0178: Choque de horarios entre mesas

## Decisión

**Se compara intervalo contra intervalo, no `weekday`+`hourtime` exacto**: `[hourtime, hourtime + duration)` sobre la semana en UTC, con **envoltura** —martes 23:00 + 3 h termina miércoles 02:00—. El intervalo es **semiabierto**: una mesa que arranca exactamente cuando la otra termina **no** choca. Cuatro reglas: **R1** un master no puede tener dos mesas vivas con agendas solapadas (`409` al guardar la agenda o crear la mesa); **R2** nadie puede postularse a una mesa que choca con otra donde ya es `Player` (`409`); **R3** un master no puede aceptar a un candidato que ya es `Player` en una mesa que choca (`409`); **R4** al aceptar a alguien, sus otras postulaciones `Candidate` que chocan **se notifican, no se rechazan**. Además, cada mesa del explorador trae un campo derivado que dice si choca con lo que el actor ya tiene comprometido, y la card lo advierte. Vive en `ScheduleConflictService` (`tables/`) y lo consume también `RegistrationService`

## Por qué

Requisito que nunca se había escrito: la agenda existía desde el schema heredado pero nada impedía que un master se comprometiera dos veces en la misma franja, ni que un jugador entrara a dos mesas simultáneas. **Por qué intervalo y no hora exacta**: una mesa de 3 h a las 20:00 y otra a las 22:00 no comparten `hourtime` y se pisan igual; comparar la igualdad dejaba pasar justo el caso frecuente. **Por qué semiabierto**: encadenar dos mesas —una termina, la otra empieza— es un uso legítimo y bloquearlo sería un falso positivo que el usuario no puede explicarse. **Por qué R4 avisa y no rechaza**: alguien manda tres postulaciones a mesas `Opened` para ver cuál sale, y hasta que lo aceptan en una no hay compromiso; elegir por él sería el sistema tomando una decisión que es suya, el mismo criterio de #70. En cambio una mesa donde ya juega **sí** es un compromiso, y por eso R2 y R3 bloquean de verdad. **Consecuencia de alcance**: R4 crea una notificación que pide una acción, así que **el retiro de una postulación se adelanta de F2 a F1** — el borrado que #175 dejó anotado. Sin eso la notificación es un callejón sin salida, exactamente el error que E1 documentó con `/my/tables`. Casos que la regla cubre y que van con test: dos filas de agenda de la **misma** mesa solapadas (`400`); dirigir y jugar cuentan igual, porque son la misma persona en la misma franja; **`Pause` no reserva horario**, porque congela la agenda (#32) y sus sesiones no ocurren; una mesa sin agenda o sin `duration` nunca choca; y editar la agenda de una mesa ya poblada **avisa** al master a quiénes les genera choque, sin expulsar a nadie. El riesgo real es la envoltura semanal: todo se guarda en UTC (#22) y la comunidad juega de noche en América, o sea de madrugada del día siguiente en UTC
