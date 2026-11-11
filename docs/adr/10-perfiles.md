---
adr: 10
titulo: Perfiles
estado: Vigente
actualizada: 2026-11-10
absorbe: [41, 44, 47, 134, 248, 249, 285]
---

# ADR-10: Perfiles

> Quién puede ver el perfil de quién y durante cuánto tiempo, qué datos propios carga cada usuario, y qué muestra la ficha de una persona según quién la abra. Las reglas que esto produce las aplica la skill `arquitectura` (`reglas-negocio.md` §Visibilidad de perfiles). La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 La visibilidad de perfiles es asimétrica y nace de la relación en una mesa

**Decisión.** El perfil de un **master** es visible para cualquier usuario que mire su mesa. El perfil de un **jugador** se abre para el master **desde que recibe su postulación**. Los **jugadores de una mesa** ven los perfiles de sus compañeros. Las tres reglas caducan igual (§2) y el admin no tiene restricciones de visibilidad salvo la autoría de los comentarios (ADR-06 §8). **El veto corta la relación en un solo sentido**: el vetado deja de ver el perfil del master **por esa mesa**, porque el veto se la oculta (ADR-09 «Postulaciones y veto» §4 «El veto se acota a una sola mesa y la hace invisible para el vetado»), y lo sigue viendo si otra mesa se lo abre; el master sigue viendo el perfil de quien vetó. Es una regla de privacidad, no de UI: el backend la aplica y el frontend la refleja.

**Por qué.** El karma existe para evaluar comportamiento, y evaluar requiere ver; pero exponer a todos ante todos convierte la plataforma en un ranking público de personas. La asimetría refleja quién decide qué: el jugador decide **si vale la pena entrar** y mira al master antes de postularse; el master decide **a quién acepta**, y mira al candidato solo cuando ese candidato se le presentó por su cuenta. Los compañeros van a jugar juntos y a comentarse entre ellos (`JJ`), así que necesitan poder evaluarse igual. El veto no es simétrico porque las dos partes necesitan cosas distintas: al vetado, el veto le oculta la mesa, y la mesa era lo único que le abría ese perfil; al master, el perfil de quien vetó le hace falta para identificarlo y levantar el veto (ADR-09 §5). Lo decidió el usuario el 2026-11-10, al correr F4.2 «La matriz de roles».

*Antes: #41, #47.*

## §2 La visibilidad caduca a los N días del cierre de la mesa, y en pausa no corre

**Decisión.** La ventana se cuenta desde `closed_at` (ADR-07 §7), cuando la mesa entra en `Finished` o `Canceled`, y dura N días: `profiles.visibility_window_days`, que arranca en **14** y se ajusta sin desplegar (ADR-17 §3). Mientras la mesa esté en `Pause` el reloj no corre y la visibilidad sigue abierta. Pasado el plazo se deja de ver el perfil de los masters y jugadores con los que se coincidió, aunque el registro de con quién se jugó se conserva.

**Por qué.** Acota la ventana en que el karma sirve para decidir —postularse, aceptar a un candidato— sin convertir la plataforma en un directorio permanente de personas. El ancla es el cierre porque es cuando la relación termina de verdad; una mesa pausada solo está detenida, y sus integrantes siguen siendo compañeros. El historial se guarda porque hace falta para el karma y la moderación, pero deja de ser visible.

*Antes: #44.*

## §3 Un perfil que no se puede ver responde `404`, igual que el veto

**Decisión.** `GET /api/v1/users/{id}/profile` responde `404` cuando el actor no cumple ninguna de las reglas de §1 y §2 — ni `403` ni `200` recortado. La pantalla sí puede explicarle la caducidad a quien tuvo la relación.

**Por qué.** Es la misma pregunta que el veto ya contestó (ADR-09 §4), y las dos respuestas tienen que decir lo mismo o la diferencia se vuelve un canal: un `403` afirma que la persona existe **y** que hubo algo entre ustedes, que es lo que la ventana cierra; bastaría probar ids hasta ver un `403`. El `200` recortado filtra la existencia igual y reparte la regla en dos lugares, con el frontend interpretando ausencias. La explicación en pantalla no filtra nada: el frontend conoce sus propias mesas cerradas y nunca aprende si el perfil existe para alguien más.

*Antes: #249.*

## §4 Nombre y país se piden la primera vez en un onboarding bloqueante, y después se editan en la configuración del perfil

**Decisión.** `name` y `country` se piden en `/onboarding` **la primera vez**, después del primer login e inmediatamente después de la invitación al servidor (ADR-05 §1). El paso **bloquea**: una guardia de ruta manda a `/onboarding` a toda sesión que todavía no lo completó, entre por donde entre, y no solo el retorno de OAuth (`OAuthCallbackPage`). **Quien ya lo completó no vuelve ahí**: la guardia lo saca de `/onboarding`, y la escritura del onboarding no vale como edición. **Después, nombre y país se editan en la configuración del perfil**, que reusa el mismo formulario sin tocar el schema. Son dos puertas: `POST /users/me/onboarding` completa el onboarding y responde `409 ONBOARDING_ALREADY_COMPLETED` si ya estaba hecho; `PATCH /users/me` edita, y responde `409 ONBOARDING_PENDING` mientras el onboarding siga pendiente. La edición es un diálogo de `/profile`, sin ruta propia: el perfil propio es de la cuenta y vive fuera de los contextos (ADR-20 «Navegación, contextos y ayuda» §7 «El perfil es de la cuenta y vive fuera de los contextos»). `name` arranca vacío, con un placeholder que pide cómo te conocen en la mesa; `country` es `CHAR(2)` ISO 3166-1 alpha-2; `discord_username` se conserva siempre. La zona horaria, si C13 «País y zona horaria los elige el usuario» (`fase-4-revision.md` §2.5) se decide por pedirla, se suma a esa misma configuración; esta sección no decide C13 ni si la zona entra también en el onboarding.

**Por qué.** Ninguna de las dos columnas tenía quién la llenara: Discord no expone país y el legacy nunca tuvo un endpoint de escritura de usuarios. Eliminarlas perdía un objetivo legítimo: que cada quien se llame como quiera **sin perder el rastro de cuál es su Discord**, que queda como identidad verificable. El paso bloquea a propósito: si se pudiera saltar, las columnas volverían a quedar nulas, y una guardia que vive solo en el callback de OAuth se salta con cualquier otra entrada (M26 «El onboarding no bloquea y se puede repetir», F4.1 «El mapa de la interfaz»). El usuario decidió el 2026-10-24 que los dos datos se puedan cambiar después, y que la puerta sea la configuración del perfil y no `/onboarding`, que hoy funciona como edición escribiendo la URL sin haberse diseñado para eso. `CHAR(2)` evita *México* / *Mexico* / *MX* y deriva la bandera del ISO sin llamar a ningún servicio externo.

*Antes: #134.*

## §5 Ni el perfil ni ninguna otra respuesta prometen karma o comentarios hasta que existan

**Decisión.** `/profile` y `/users/:id` muestran nombre, usuario de Discord, país, roles, mesas terminadas (§6) y la asistencia agregada (ADR-08 §5). **La respuesta no lleva `karma` ni `comments`** —ni siquiera en `null`— y la pantalla **no dibuja** la sección «Comentarios recibidos». **Lo mismo vale para toda respuesta**: `MasterSummaryResponse`, `TablePlayerResponse`, `RegistrationResponse` y `UserDetailResponse` (`/users/me`) no llevan `karma`, y el explorador, el detalle de mesa, los jugadores y los candidatos del master no lo pintan. La columna `users.karma` y `User.karma` se quedan: el schema no cambia. Las dos llegan en F5 con `KarmaService` y `comments` (ADR-01 §3), y entonces vuelven en todas esas respuestas a la vez.

**Por qué.** El perfil se construyó antes que el karma para estrenar las reglas de visibilidad (§1–§3), que hacen falta funcionando para decidir quién puede comentar a quién, en vez de construir el karma sobre una puerta que nadie abrió. El bloque vacío no se dibuja porque una sección titulada «Comentarios recibidos» sin nada abajo no dice «todavía nadie te comentó» sino «esto debería tener algo» (`diseno` §5). Y el campo no viaja en `null` porque un DTO que promete `karma` le enseña al frontend a preguntarlo. En las demás respuestas el número era peor que un hueco: nada escribe la columna hasta F5, así que siempre valía 8000, el default, y un número que no se mueve se lee como una reputación real sin decir nada.

*Antes: #248.*

## §6 La ficha de una persona depende de quién la abre: expediente para el admin, consulta corta para el master

**Decisión.** Cada persona de una mesa —masters, jugadores y candidatos— tiene un indicador «Ver ficha». **El admin** abre `/admin/users/:id`: usuario de Discord, país, miembro desde, mesas terminadas, roles y estado **con las acciones que los cambian** (las mismas reglas que la fila de la lista), asistencia, **todas las mesas** que dirige, juega o a las que se postuló —abriendo `/admin/tables/:id`— y el historial de lo que hicieron los admins con la cuenta. La lista de usuarios abre la ficha por el usuario de Discord y por un ícono en toda fila. **El master** abre un modal (`PersonSummaryDialog`): usuario de Discord, nombre y mesas terminadas, nada más. En la vista del master, «Ver ficha» está también en cada persona que nombran las pestañas Sesiones, Peticiones, Archivos e Historial, con el mismo `PersonSummaryDialog` de Jugadores y Candidatos. Las pestañas no saben cuál abren: reciben `onOpenPerson` y lo decide la pantalla que las monta. El perfil (`ProfileResponse`) suma `discordUsername` y `finishedTables` —mesas `Finished` donde fue `Player`— bajo las reglas de §1–§3. `GET /users/{id}/tables`, solo admin, lista las mesas de una persona: masters y postulaciones mezclados y paginados en memoria como la bandeja (ADR-16 §5), la más nueva primero, sin mesas dadas de baja ni postulaciones retiradas (ADR-03 §7).

**Por qué.** El admin necesita toda la información de la persona y poder actuar desde ahí; el historial suelto «realmente no sirve». La línea entre roles la fijó el usuario: **el master solo ve información muy general**, y es muy importante distinguirlo. Página para el admin porque es un expediente con acciones y una lista que crece; modal para el master porque es una consulta al pasar sin salir de la mesa. El usuario de Discord lo ve cualquiera que ya podía ver ese perfil: es una comunidad de un servidor de Discord y así se encuentran, así que no abre nada que el servidor no muestre. Las demás pestañas del master nombraban personas sin dejar abrirlas (M25 «Lo que el jugador no tiene adónde ir», F4.1 «El mapa de la interfaz»); el diálogo es el mismo porque la línea entre roles no cambia según la pestaña.

*Antes: #285.*

## §7 Las mesas terminadas de un perfil se pueden abrir

**Decisión.** El número de «Mesas terminadas» (§6) se enlaza en los dos perfiles. **En el propio** (`/profile`) lleva a `/player/history`. **En el ajeno** (`/users/:id`) abre un listado de las mesas terminadas de esa persona, con lo que ya es público de cada mesa —nombre, sistema, master, fecha de cierre—, y cada fila enlaza al detalle público de la mesa (`/player/tables/:id`), que abre una mesa terminada. El listado ajeno exige un endpoint nuevo; su forma la fija el plan de implementación.

**Por qué.** El perfil contaba mesas terminadas sin dejar ver cuáles (M25 «Lo que el jugador no tiene adónde ir», F4.1 «El mapa de la interfaz»): un número que no se puede abrir no explica nada (`diseno` §1, principio 4 «La lista es la explicación»). Lo propio ya tenía pantalla, el historial; lo ajeno no, y se arma solo con lo que la mesa ya muestra en su detalle público, así que no expone nada de la persona que su perfil no diga.
