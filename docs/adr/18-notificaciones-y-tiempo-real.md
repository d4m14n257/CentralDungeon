---
adr: 18
titulo: Notificaciones y tiempo real
estado: Vigente
actualizada: 2026-10-24
absorbe: [14, 101, 116, 153, 154, 156, 157, 244]
---

# ADR-18: Notificaciones y tiempo real

> Qué es una notificación, qué eventos la producen, cómo se ve y se usa la campanita, y cómo llegará el tiempo real. Las reglas de código están en `arquitectura` §2 y §3.3 (`staleTime`), y la forma visual en `diseno`. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Las notificaciones son solo in-app: la fila es la notificación

**Decisión.** En v1 una notificación es una fila en `notifications`, sin email ni Discord DM, sin preferencias ni suscripciones: todo destinatario de un evento recibe su fila. La fila guarda el tipo y sus parámetros, y el frontend arma la frase en el idioma elegido (ADR-23 §3).

**Por qué.** Es lo mínimo que cubre el caso y no cierra ninguna puerta: si se agrega otro canal, es una tabla de canal aparte sin tocar la existente.

*Antes: #14.*

## §2 El tiempo real es WebSocket + STOMP, y el mensaje es una señal de invalidación

*El WebSocket es diseño para F6, no construido. Hasta entonces, lo que cambia por actos ajenos se pide por tiempo: la bandeja de admins cada 15 s (ADR-16 §8) y la campana cada 60 s.*

**Decisión.** `/ws` con STOMP, el JWT en el frame `CONNECT` y no en la query string. Destinos: `/user/queue/notifications` para lo personal, `/topic/admin-queue` para la bandeja y `/topic/tables` para el explorador de mesas. **El mensaje trae tipo e id, no el contenido**: el cliente invalida solo esa rama de `queryKeys`, confía en que `invalidateQueries` refetchea solo las queries activas, y **al reconectar invalida todo lo activo**. **El `staleTime` del `QueryClient` (30 s, `staleTime.tableList`) es la política**: una query declara el suyo, de la tabla de `arquitectura` §3.3, solo cuando es otro. Mientras no exista el WebSocket, la bandeja se refresca por intervalo (ADR-16 §8) y **la campana también: cada 60 s y al volver a la pestaña** (`live.notifications` en `config/query.ts`); el polling se borra cuando llegue el socket.

**Por qué.** WebSocket sobre SSE deja abierta la puerta a lo bidireccional (chat, presencia) sin cambiar de transporte, a cambio de handshake, heartbeat y reconexión. El token va en el frame porque el navegador no manda headers en el handshake y la query string queda en los logs de acceso. Mandar la señal y refetchear mantiene la caché de TanStack Query como única fuente (regla dura 11), y un mensaje perdido no deja a la UI inventando datos. Invalidar solo una rama evita que cada publicación mueva media caché de cada cliente conectado; invalidar todo al reconectar tapa los mensajes perdidos mientras el socket estuvo caído. El default de la librería para `staleTime` es `0`, así que sin decidirlo cada montaje vuelve a pedir todo; un default propio en el cliente lo decide una vez, y declararlo además en cada query que coincide con él solo repetía el número. La campana guarda sus datos con `staleTime` `Infinity`, y una notificación la escribe el acto de otra persona, que ninguna mutación del lector ve: sin polling no se refrescaba hasta recargar la página. Un minuto alcanza para avisos que no son urgentes, y el refetch al volver a la pestaña atrapa al que regresa. **Límite conocido**: el broker en memoria de Spring sirve para una sola instancia; con más hace falta un broker externo.

*Antes: #101, #116.*

## §3 Notifica lo que alguien tiene que saber y no tiene otra forma de enterarse

**Decisión.** Una postulación nueva notifica `NewCandidate` a **todos** los masters de la mesa, `Primary` y `Secondary`, con el nombre del postulante (`name`, o `discordUsername` si no hay). `MasterAssigned` avisa a quien un admin hace master de una mesa o suma como co-master; promover a quien ya la dirigía no notifica. El ciclo de revisión notifica `TableApproved` y `TableChangesRequested`; que un admin editó la mesa lo dice `TableEditedByAdmin`, con la lista de lo que cambió. La razón de un pedido de cambios **no** viaja en la notificación: vive en el historial de estados. **Quienes están unidos a una mesa —sus jugadores aceptados y sus masters vivos, `Primary` y `Secondary`— reciben aviso de los cambios en ella**: que se pausa (incluida la aprobación de una pausa pedida por un master, que les llega también a los co-masters y no solo a quien la pidió), que se reanuda, que se cancela, que termina y que cambia su agenda. Vale haga el cambio un master o un admin; los avisos `Table…ByAdmin` a los masters (ADR-06 «Roles y alcance» §8) son parte de esto. **Cómo se implementó** (2026-10-27): los tipos son `TablePaused`, `TableResumed`, `TableCanceled`, `TableFinished` y `TableScheduleChanged`. Un master recibe en su lugar la variante `…ByAdmin` cuando el cambio lo hizo un admin que no dirige la mesa. **Quien hizo el cambio no recibe el aviso**, y en una pausa aprobada tampoco quien la pidió, que ya recibe `ApprovalRequestApproved`. Cada persona recibe un solo aviso, aunque sea master y jugador a la vez. Los parámetros llevan `membership` (`Master` o `Player`), porque el mismo aviso abre pantallas distintas según quién lo lee. `TableEditedByAdmin` lleva en `changes` los campos que cambiaron, y si la edición no cambió nada no se avisa. El aviso de agenda sale solo si la agenda cambió de verdad y la mesa ya es pública. La reanudación ya no manda además `SessionScheduled`. **`ScheduleConflict` le llega solo a los candidatos**: un jugador no tiene nada que resolver en `/player/applications`, y del cambio de agenda ya se entera por `TableScheduleChanged`.

**Por qué.** Cualquiera de los dos masters puede aceptar o rechazar candidatos, así que avisar solo al `Primary` dejaría afuera a quien también puede resolver. Asignar un master es la única notificación cuyo destinatario no hizo nada para provocarla, y sin ella se enteraba solo si entraba a un contexto que nunca había visto. La revisión era una espera sin otro lado visible. Que te editen lo que escribiste es otra noticia, y por eso la edición de un admin avisa aparte, diciendo qué tocó; `TableApprovedWithChanges` se borró porque nada lo producía (M31 «Lo construido que nadie usa»). La razón no se copia porque una segunda copia puede discrepar de la primera (ADR-23 §3). Los cambios de la mesa los decidió el usuario el 2026-10-24 (M29 «Notificaciones que faltan o sobran», F4.1 «El mapa de la interfaz»): ningún aviso llegaba a los jugadores cuando su mesa se pausaba, se cancelaba o terminaba, y aprobar una pausa avisaba solo al solicitante. Para quien está unido a la mesa, esos cambios mueven su semana, y no tiene otra forma de enterarse que entrar a mirar.

*Antes: #153, #244.*

## §4 La campanita muestra las últimas 5, con el diseño del mock

**Decisión.** El panel sigue `sc_comp_shell` de `design/build.py`: encabezado «Notificaciones · N sin leer»; cada fila con título y hora relativa, punto lleno y fondo `raised` si no está leída, punto hueco y texto atenuado si ya se leyó; separador y «Ver todas» al pie. El panel no muestra el mensaje; `/notifications` sí. `notificationsApi.list()` pide `size=5` siempre, así que panel y página coinciden sin recortar en el cliente. La hora relativa la calcula `lib/relativeTime.ts`, que devuelve unidad y conteo y deja el texto a `t()`. El hover de una notificación toma el hover y el cursor del token `--color-accent` y de `base.css` (ADR-21 §4), sin forzar nada en el componente, en el panel, en «Ver todas» y en `/notifications`.

**Por qué.** El mock ya traía la distinción leída/no leída y la hora relativa; el panel nunca se había construido contra él. El límite va en la fuente para no duplicar la lógica. El hover necesita un tono propio porque `--color-accent` **es** `--color-raised`: sobre una fila no leída, el resaltado de base no cambiaba nada visible, y `cursor-default` tampoco decía que la fila se puede clickear.

*Antes: #154, #157.*

## §5 Clickear una notificación la marca leída, cambia de contexto si hace falta y navega

**Decisión.** `features/notifications/lib/notificationTarget.ts` mapea cada tipo a una ruta (`RegistrationAccepted`/`RegistrationRejected` → Jugador, detalle público; `NewCandidate` → Master, detalle de master), y `useNotificationClick()` marca como leída y navega —el contexto lo da el prefijo de la ruta de destino (ADR-20 §2); el store queda solo como respaldo—, en el panel y en `/notifications`. `/notifications` sigue `sc_notifications`: fila entera clickeable, punto de leído, badge solo para los desenlaces cerrados (aceptada, rechazada), hora con «hace». `PATCH /api/v1/notifications/read-all` respalda «Marcar todo como leído», visible solo si hay algo sin leer. El contexto persistido se acota contra los contextos disponibles de la cuenta actual, con `player` por defecto.

**Por qué.** Una fila puramente informativa no dice cómo deja de estar sin leer ni lleva a ningún lado, y una notificación de Master vista en contexto Jugador tiene que resolver el cambio de contexto además de la navegación. El contexto persistido en `localStorage` no está atado a la cuenta: en un navegador compartido quedaba el de la sesión anterior. No es una brecha —el backend reverifica la pertenencia en cada endpoint y el chip sale de `me.roles`—, pero el logo apuntaba a `/master/tables` para una cuenta sin ese contexto.

*Antes: #156.*
