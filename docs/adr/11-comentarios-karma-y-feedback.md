---
adr: 11
titulo: Comentarios, karma y feedback
estado: Vigente
actualizada: 2026-10-20
absorbe: [17, 30, 31, 35, 40, 43, 46, 48, 49, 50, 51, 52, 74, 82, 91, 93, 94, 95, 96, 97, 98, 99]
---

# ADR-11: Comentarios, karma y feedback

> **Diseño cerrado para F5, sin construir**: del subsistema hoy solo existen el DDL de `V1` y la columna `users.karma`.
>
> Cómo la comunidad se evalúa entre sí —comentarios anónimos sobre personas con quienes se jugó, moderados y convertidos en karma— y cómo opina sobre el sistema mismo. El modelo de tablas y las reglas que reemplazaron a los triggers están en `arquitectura` §4; esta ADR dice *qué* se decidió y *por qué*.

## §1 El karma va de 0 a 10000, arranca en 8000 y es una señal, no una sanción

**Decisión.** Se retoma el diseño de datos que ya traía el schema heredado (`comments.comment_type`, `comments.karma_impact`, `users.karma`). El karma vive en una escala 0–10000, todo usuario arranca en 8000, y sube o baja según los comentarios aprobados que recibe (§8). Llegar a 0 o a 10000 **no dispara ninguna acción automática**: el piso pone a la persona en el radar de los admins, y un baneo del servidor o de la página es siempre una decisión manual.

**Por qué.** Era el diseño más elaborado del schema viejo para una feature que nunca se construyó, y no había una alternativa mejor que justificara rehacerlo. El karma es el mecanismo con el que la comunidad regula actitudes en lugar de vetos que persigan al usuario (ADR-09 §4). Automatizar una sanción sobre un número agregado castigaría casos que ningún algoritmo juzga bien; como en ADR-15 §2, el sistema informa y las personas deciden.

*Antes: #17, #30, #74.*

## §2 Un comentario evalúa a alguien con quien se jugó, uno por autor y persona en cada mesa

**Decisión.** Un comentario sobre una persona nace de la mesa en que se jugó junto a ella, se tipifica como positivo, neutro o negativo (`karma_impact`), y conserva las tres direcciones heredadas: `JJ` (jugador→jugador), `JM` (jugador→master) y `MJ` (master→jugador). **Cada autor deja como máximo uno sobre la misma persona por mesa** —no por sesión—; sobre la misma persona en otra mesa puede dejar otro.

**Por qué.** Un comentario es una opinión sobre alguien con quien se jugó, así que solo tiene sentido dentro de una mesa concreta, que además verifica que ambos estuvieron ahí. El límite es antispam: sin él se hunde o se infla el karma de alguien repitiendo comentarios, y «uno por sesión» permitiría acumular diez sobre la misma persona en una campaña larga, justo lo que la regla existe para evitar.

*Antes: #31, #35.*

## §3 El comentario se escribe en borrador y cuenta recién cuando se confirma al cierre

**Decisión.** Se puede escribir en cualquier momento de la mesa, pero queda en **borrador**. Cuando la mesa llega a `Finished`, el autor lo revisa —lo deja o lo corrige— y recién al confirmarlo pasa a ser un comentario que puede contar para el karma. No hay un segundo comentario al cierre: antes de confirmar, el frontend advierte que es el único que podrá dejar sobre esa persona en esa mesa y que el mejor momento para evaluar es el final. Es una confirmación explícita, no una restricción de fecha.

**Por qué.** Se resuelven tres tensiones a la vez: la impresión en caliente no se pierde, la versión que cuenta es la revisada al final, y no hay asimetría entre quien comentó temprano y quien esperó. Un segundo comentario al cierre se descartó porque duplicaba el peso que una sola persona puede mover sobre el karma de otra; el problema real —a mitad de camino la opinión no está formada— se resuelve avisando.

*Antes: #40, #48.*

## §4 Una mesa cancelada no genera comentarios ni mueve karma

**Decisión.** Solo una mesa que llega a `Finished` habilita confirmar comentarios. Si termina en `Canceled`, sus borradores nunca se validan: no entran al karma ni se moderan, y se purgan como cualquier borrador vencido (§6). La visibilidad de perfiles sí corre su ventana (ADR-10 §2).

**Por qué.** Una cancelación suele ser por fuerza mayor —el master se enfermó, el grupo se cayó— y no dice nada del comportamiento de nadie. Permitir comentarios ahí convertiría la mala suerte en karma negativo para gente que no hizo nada. Con el borrador de §3 no hace falta anular ni revertir nada: el borrador simplemente nunca llegó a ser un comentario. Que no se comente no significa que la mesa no haya existido.

*Antes: #46.*

## §5 El autor de un comentario es anónimo para todos, sin excepción

**Decisión.** Nadie puede saber quién escribió un comentario confirmado: ni los admins, ni el owner, ni quien desarrolla el sistema. Lo único visible es su dirección (`JJ` / `JM` / `MJ`). `comments` no tiene columna de autor, y tampoco de mesa: la mesa solo la conoce el borrador (§6). La cuota antispam de §2 se guarda como **token opaco** `HMAC(secreto, autor + comentado + mesa)` con un `UNIQUE` sobre el token, y **el secreto vive fuera de la base** (variable de entorno o gestor de secretos). Con eso `comments.created_at` es una columna normal. El «ver como» de los admins queda fuera de todo lo que toque comentarios (ADR-06 §9), y la auditoría no registra `comments` ni `comment_drafts` (ADR-17 §1).

**Por qué.** Requisito duro del cliente, no negociable y sin «salvo para moderación»: si el autor puede identificarse, un jugador no deja una reseña negativa sobre un master del que depende para entrar a futuras mesas, y el karma pasa a medir prudencia política en vez de comportamiento. Por eso la FK `comments.user_created_id` del schema heredado no podía quedarse, y por eso una tabla de cuota con la tupla `(autor, comentado, mesa)` en claro incumplía el requisito por sí sola: esa tabla *es* el registro de quién comentó sobre quién. Con el HMAC la restricción se sigue verificando y un volcado de la base no permite reconstruir nada.

*Antes: #43, #82.*

## §6 El autor solo existe en el borrador, y un borrador que no se confirma se purga

**Decisión.** La autoría vive en `comment_drafts` y solo ahí: tabla privada, visible únicamente para su autor. Al confirmar se crea la fila anónima en `comments` y el borrador desaparece. Un borrador que no se confirma **expira**; antes se le notifica al autor al terminar la mesa y otra vez antes de que cierre la ventana. Expirar —o cancelarse la mesa— marca `deleted_at` **y purga el contenido: se vacían `author_id` y el texto**. Ninguna otra tabla —`comments`, `audit_logs`, `notifications`— guarda jamás quién escribió qué.

**Por qué.** Es lo que hace compatibles el borrador de §3 con el anonimato de §5: la autoría existe mientras el comentario no cuenta para nada, y deja de existir justo cuando empieza a contar. Confirmar es un acto deliberado y no se suple con silencio, así que lo no confirmado no se publica. La purga es la única excepción al borrado lógico (ADR-03 §7): una fila marcada como borrada sigue siendo legible, y un borrador vencido completo es exactamente el registro de «quién iba a decir qué sobre quién» que §5 prohíbe. Vaciarlo conserva el dato agregado útil —cuánta gente escribe y no confirma—. La garantía es sobre la base viva; la fila sobrevive en backups y binlog lo que estos retengan.

*Antes: #49, #50, #52.*

## §7 Todo comentario confirmado pasa por un admin, y solo los aprobados mueven karma

**Decisión.** Cada comentario confirmado entra a moderación: un admin lee el contenido y el destinatario —nunca el autor— y decide. Los estados son los del schema heredado (`Under review` / `Reviewed` / `Rejected` / `Deleted`). Un `Rejected` no le llega a la persona ni la afecta; el karma se calcula solo con los aprobados.

**Por qué.** Se evaluó moderar solo los reportados y se descartó: el karma tiene efecto real sobre quién entra a una mesa, y un comentario injusto que ya movió puntos es difícil de deshacer. Consecuencia operativa: el karma se mueve al ritmo en que los admins moderan, así que la bandeja de moderación es parte del producto, no un extra.

*Antes: #51.*

## §8 El karma es un promedio bayesiano con prior 0.8, peso W = 20 y semivida de 12 meses

**Decisión.** Cada comentario aprobado aporta un valor `v` y un peso `w`:

| `karma_impact` | Significado | Valor `v` |
|---|---|---|
| positivo | «me gustó jugar con esta persona» | `1.0` |
| neutro | «jugamos, sin nada que destacar» | `0.8` — **igual al prior** |
| negativo (`Reported`) | «me desagradó por su actitud» | `0.0` |

El peso decae con la antigüedad: `w = 2^(−antigüedad / H)`.

```
karma = 10000 × ( W·m + Σ wᵢ·vᵢ ) / ( W + Σ wᵢ )

  m = 0.8    prior, el 8000 de arranque
  W = 20     peso del prior, en "comentarios equivalentes"
  H = 12 meses
```

`W` y `H` son **configuración, no constantes de código**: ajustarlos no requiere migración. Cómo se comporta:

| Situación | Karma |
|---|---|
| Usuario nuevo, sin comentarios | **8000** |
| 1 positivo | 8095 |
| 3 positivos | 8261 |
| 10 positivos | 8667 |
| 30 positivos | 9200 |
| 1 negativo | 7619 |
| 3 negativos | 6957 |
| 10 negativos | 5333 |
| 50 negativos | 2286 |
| 20 neutros y nada más | **8000** (no mueve) |
| 20 neutros + 1 negativo | 7805 |
| 10 positivos + 3 negativos | 7879 |
| 3 negativos de hace 12 meses | 7442 (vs 6957 si fueran de hoy) |
| 3 negativos de hace 24 meses | 7711 |

Consecuencias: (1) **el karma es un valor derivado, no un contador**: como los pesos decaen, cambia sin que ocurra ningún evento, así que se recalcula (§9); `users.karma` es una proyección cacheada y la fuente de verdad son las filas de `comments`. (2) **Se calcula sin saber quién comentó**: solo usa destinatario, `karma_impact` y `created_at`, que es lo que `comments` guarda (§5). (3) Solo cuentan los aprobados (§7). (4) **Los extremos son asintóticos**: llegar a 0 o a 10000 exige decenas de comentarios sostenidos en un sentido, así que el rango real es más angosto que la escala, coherente con §1.

**Por qué.** La base es el *Beta Reputation System* (Jøsang & Ismail, 2002), pensado para reputación entre pares con pocos datos y comportamiento que cambia, que cuenta evidencia positiva y negativa con un factor de olvido; el promedio bayesiano con prior es su forma práctica y ancla el 8000 de manera natural. Se descartaron el *Wilson score* (sirve para ordenar, no para un puntaje absoluto, y no admite un prior en 0.8: hunde al usuario nuevo) y *Elo/Glicko* (necesitan partidas con ganador; acá nadie compite). Cumple los cinco criterios fijados: rendimientos decrecientes, peso por antigüedad, proporción y no total, resistencia a pocos datos (con cero comentarios queda en 8000) y explicable en una frase: *«partís de 8000 y cada comentario tira del promedio; los viejos pesan menos»*. Con `W = 20` un negativo aislado cuesta ~380 puntos y hacen falta tres para bajar de 7000: castiga un patrón, no un mal día. Con `H = 12 meses` un negativo de hace un año pesa la mitad, y uno puede recuperarse sin borrar el historial. **El neutro vale el prior y no 0** para que no mueva el promedio pero sí acumule confianza: sin eso, 20 neutros y 1 negativo valdría lo mismo que 1 negativo solo (7805 contra 7619).

*Antes: #96.*

## §9 El karma se recalcula al aprobar un comentario y en un job semanal nocturno

**Decisión.** Dos disparadores: al aprobar un comentario se recalcula el karma de su destinatario, y un job semanal nocturno recalcula el de todos.

**Por qué.** Como el karma cambia sin eventos (§8), hace falta un recálculo periódico, pero recorrer toda la base cada noche no compra nada: con `H = 12 meses` el peso de una valoración cambia un **1,3 % por semana**, unos pocos puntos sobre 8000. El recálculo puntual da respuesta inmediata donde importa: el afectado ve el efecto en el momento.

*Antes: #97.*

## §10 Se muestran el puntaje y los comentarios, sin desglose, y la asistencia va aparte

**Decisión.** Del karma se muestran el puntaje y los comentarios recibidos, sin un resumen agregado («X positivos, Y negativos»). Lo que ve el dueño del perfil es lo mismo que ve un master al evaluarlo, sujeto a las reglas de visibilidad de perfiles (ADR-10 §1, §2). La asistencia **no entra en el cálculo**: se muestra como métrica separada junto al perfil.

**Por qué.** Los comentarios mismos son la explicación, y leerlos dice más que contarlos; un desglose sería una segunda representación del karma que puede desincronizarse de la primera. Que ambos vean lo mismo elimina la pregunta de quién tiene información privilegiada. Mezclar asistencia y karma en un número lo vuelve imposible de explicar (§8): separados, un master ve «karma 7800» y «faltó a 3 de 10 sesiones» y saca su conclusión. Si algún día se fusionan, la asistencia ya está registrada (ADR-08 §4).

*Antes: #98, #99.*

## §11 El feedback sobre el sistema es anónimo, va en su propia tabla y no se modera

**Decisión.** La opinión sobre el sistema (el tipo `General` del enum heredado) no es un comentario sobre una persona: no tiene destinatario, no mueve karma, no se ata a una mesa ni a una sesión y no consume la cuota de §2. Vive en su propia tabla, **`system_feedback`, sin columna de autor**: nace anónima y no pasa por el circuito de borrador. Va directo a una bandeja de lectura para admins y owner, **sin moderación**; su `status` sirve para marcar lo ya revisado, no para aprobar o rechazar.

**Por qué.** Sirve para que la comunidad proponga mejoras desde la v1, y rompe casi todas las reglas de los tipos que evalúan personas, así que compartir tabla con ellos no tenía sentido. El anonimato protege algo distinto que en §5 pero igual de real: que alguien opine mal del sistema sin quedar mal ante los admins o el owner, que son quienes lo leen; sin anonimato solo llega la opinión cómoda. No necesita borrador porque no hay mesa que cerrar, y no se modera porque no hay una persona afectada a quien proteger.

*Antes: #91, #93, #95.*

## §12 Un feedback cada 24 horas, verificado con un token rotativo por hora

**Decisión.** Cada usuario envía como máximo un feedback cada 24 horas reales. Al escribir se guarda el token `HMAC(secreto, user_id + hora_UTC)` de la franja actual; para verificar, el service calcula los tokens de las **últimas 24 franjas horarias** y comprueba si alguno existe. Las filas de token se purgan pasadas 24 h. La ventana efectiva queda entre 23 y 24 h, siempre del lado seguro.

**Por qué.** El día calendario dejaba escribir a las 23:59 y otra vez a las 00:01. Un token fijo por usuario con marca de último envío —lo más directo para una ventana móvil— crea un **seudónimo permanente**: una fila por persona que permite agrupar todos sus feedbacks en el tiempo y, con el contenido a la vista, deducir la identidad. El token por franja rota, no persiste y no enlaza dos envíos separados por más de un día.

*Antes: #94.*
