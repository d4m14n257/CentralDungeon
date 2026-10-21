# 1 y 2 Principios y navegación

> Parte de la skill `diseno`. Hasta #292 eran `docs/frontend-diseno.md` §1 y §2, y conservan su numeración: «principio 2 de `frontend-diseno.md` §1» es el principio 2 de `diseno` §1. Cómo se implementan el ruteo y las guardias está en `arquitectura` §3.1.6; el mapa dibujado, un nodo por ruta, en `docs/diagramas/21`–`24`.

## 1. Principios

Cuatro, para poder resolver discusiones sin volver a discutirlas:

1. **El estado de la mesa siempre visible.** Una mesa tiene nueve estados y casi todo lo que se puede hacer depende de en cuál está. El estado va en la card, en el detalle y en cualquier listado — nunca hay que adivinarlo ni entrar a buscarlo.
2. **No se muestra lo que no se puede hacer.** Si un jugador no puede postularse porque la mesa está llena o porque ya tiene una postulación activa, el botón no aparece deshabilitado sin explicación: se reemplaza por el motivo. Un botón gris que no dice por qué está gris es peor que no tener botón.
3. **Todo lo que crea, cambia o borra se confirma antes de escribirse** (#283; antes decía «lo irreversible», #27, #39, #40). Un formulario o un diálogo, al enviarse, abre un **paso de revisión** —`ConfirmDialog` encima del formulario— que dice **qué** se va a crear o cambiar, **a quién o a qué** afecta, y **qué pasa después**, nunca un «¿Estás seguro?» genérico; una acción de un clic o un gesto (una fila, un menú, conectar en el lienzo) también. Cancelar no escribe nada y deja el formulario como estaba. Un flujo que ya tiene su propio paso de revisión lo cumple sin otro encima (postularse a una mesa). **Quedan afuera, a propósito**, lo que no cambia datos de nadie (descargar, cerrar sesión, marcar como leída al abrir una notificación) y lo de bajo riesgo que se deshace con el mismo botón y se repite mucho: marcar todo como leído, la asistencia jugador por jugador y tomar o soltar una fila de la bandeja — confirmar cada clic volvería lenta la tarea (pasar lista a seis jugadores serían doce clics).
4. **La lista es la explicación.** En vez de resumir con números agregados, se muestran los elementos: el karma se explica con los comentarios recibidos (#99), no con un gráfico de barras.

## 2. Navegación

### Cambio de contexto explícito

Los roles son acumulables y sin jerarquía (#37, #89): alguien puede ser `Player` y `Master` a la vez, o `Master` sin ser `Player`. En vez de un menú que crece mezclando actividades sin relación, hay un **selector de contexto** arriba:

```
┌────────────────────────────────────────────────────────┐
│  CentralDungeon    [ Jugador ▾ ]        🔔 3    ( AV ) │
└────────────────────────────────────────────────────────┘
                       │
                       ├── Jugador
                       ├── Master
                       ├── Admin
                       └── Owner      ← solo los que tenga
```

- **Quien tiene un solo rol ve el chip igual, pero sin caret ni menú** (#145): no hay nada que elegir, pero sí algo que mostrar — sin esto el header no da ninguna señal de en qué contexto está.
- **El contexto Master aparece con el rol `Master` o con al menos una fila viva en `masters`** (#135). Un jugador al que un admin asignó como master de una mesa entra por ahí, ve solo esa mesa, y **no ve `/master/tables/new`**: dirigir no es crear.
- **El chip dice dónde estás, no qué elegiste** (#222). Cada contexto es dueño de un prefijo —`/player`, `/master`, `/admin`—, así que el contexto sale de la URL. Un master que abre `/player` lee «Jugador», porque ahí es donde está. Las pantallas transversales (`/notifications`, `/my/schedule`) no son de ningún contexto y conservan el que traía quien las abrió.
- El contexto se recuerda en Zustand + `localStorage`, pero **solo como respaldo** para esas transversales y para elegir a dónde despacha `/`. Solo se recuerda un contexto que la cuenta tenga: por defecto `Jugador` si lo tiene, si no el primero disponible.
- **Al entrar siempre se cae en la home del contexto propio** (#222). `/` no tiene pantalla: mira los contextos de la cuenta y reenvía. El retorno del OAuth, el onboarding y el 404 apuntan ahí y no recalculan el destino cada uno.
- **Cada contexto se cierra a quien no lo tiene, y se lo devuelve a su home** (#269). `/player/*` pide el rol `Player`; `/master/*`, el rol `Master` o una fila viva en `masters` (#135); `/admin/*`, `Admin` u `Owner`. Quien fuerza la ruta de un contexto ajeno **no ve ni su nav ni un «sin permiso»**: el layout lo manda a `/` y `/` lo despacha a lo suyo. La regla es la misma que decide qué contextos lista el chip, así que el chip y la puerta nunca discrepan. Las transversales no son de ningún contexto y no se cierran.
- **Aun así, el contexto no es la seguridad.** El backend sigue autorizando endpoint por endpoint y responde `403` igual (#103, #121): la puerta del frontend decide qué se muestra, no qué se permite. `ForbiddenState` queda para el otro rechazo — un recurso concreto, dentro de un contexto que sí tenés, que no es tuyo.
- Las notificaciones y el avatar son globales: no dependen del contexto.
- **El feedback del sistema también es global y vive en el layout, no en una ruta** (#133). No tiene pantalla propia porque no tiene contenido que mostrar: es una acción. Se abre desde el shell, en cualquier contexto, y manda a `system_feedback` — anónima (#93), una cada 24 h (#94), directo a la bandeja de admins sin moderación (#95). Como el límite es del servidor, la interfaz **no lo predice**: ofrece el botón siempre y explica el `429` si toca.

### Sitemap

| Contexto | Ruta | Pantalla |
|---|---|---|
| Público | `/login` | Entrar con Discord |
| | `/auth/callback` | Retorno del OAuth, incluye el paso de invitación al servidor (#38) |
| | `/onboarding` | **Solo la primera vez**: nombre a mostrar y país. Bloquea hasta completarse (#134) |
| | `/` | **No es una pantalla**: despacha a la home del contexto de quien entra (#222) |
| **Jugador** | `/player` | Explorar mesas. **Home del contexto**. **El buscador es su único filtro** (#242, misma lectura que `/my/files`): los filtros por sistema, tag y plataforma que esta fila describía son los comandos `/table_system`, `/table_tag` y `/table_platform`, que acotan desde la misma línea y se combinan entre sí — y un término sin comando busca el nombre de la mesa. **Los tres son texto libre** (#246): el backend resuelve el grupo de sinónimos, así que buscar por uno encuentra las mesas etiquetadas con cualquier otro (#54, #56). Lo buscado vive en `?q=` (#185); la página no, porque el listado se acumula con «Ver más» (#173) |
| | `/player/tables/:id` | Detalle de una mesa y postulación. Cabecera estándar (#281): estado junto al nombre, masters como descripción, «Volver a explorar», y **«Postularme» a la derecha** —antes al pie de la tarjeta—; el choque de horario se explica justo debajo |
| | `/player/applications` | Mis postulaciones y en qué estado están |
| | `/player/my-tables` | Mesas donde soy jugador — **solo las vivas** |
| | `/player/my-tables/:id` | Mi mesa: agenda, sesiones, peticiones pendientes. Cabecera estándar (#281): estado junto al nombre, «Volver a mis mesas», el enlace a la ficha pública como descripción |
| | `/player/history` | Mesas terminadas y canceladas, con la asistencia final (#133) |
| | `/my/files` | Mis archivos: a qué flujos pertenece cada uno (#233) y dónde se usa hoy (#232), reutilizables al adjuntar (#65). **Es el único lugar que pregunta el cajón**, porque es el único sin flujo del que deducirlo — y solo ofrece los que son tuyos (#237): los de jugador a cualquiera, los de master a quien dirija mesas, y nunca los anuncios. **Transversal, no del contexto Jugador** — lo que alguien subió como jugador y como master es una sola biblioteca (#222). **Es de players y masters** (#241): quien no tenga ninguno de los dos roles no la ve en el menú y la pantalla le dice por qué. **El buscador es su único filtro** (#242), y la lista va paginada de a 20 con divider entre filas |
| | `/player/profile` | Mi karma y los comentarios que recibí |
| | `/player/users/:id` | Perfil de otra persona, sujeto a #41, #44 y #47 |
| Transversal | `/my/schedule` | **Mi horario**: la semana entera en una grilla — lo que dirigís y lo que jugás, junto. De ningún contexto a propósito: son las mismas noches (#227) |
| | `/notifications` | Historial de notificaciones. **De ningún contexto**: no cambia el chip (#222) |
| | ~~`/help`~~ · ~~`/help/players`~~ · ~~`/help/masters`~~ · ~~`/help/admins`~~ | **Ya no son pantallas** (#231). La ayuda se lee en un diálogo levantado desde la pantalla que provoca la pregunta —`<HelpLink section="masters.schedule">`—, porque navegar a leerla tiraba el wizard a medio llenar. Las secciones y sus textos siguen enteros, en `features/help/sections/`. **`/help` queda reservada para soporte**: pedir asistencia, reportar un error. No se registra hasta que exista |
| **Master** | `/master` | Dashboard: qué necesita tu atención hoy, en todas tus mesas (#136) |
| | `/master/tables` | Mis mesas como master |
| | `/master/tables/new` | Wizard de creación — **solo con el rol `Master`** (#135) |
| | `/master/tables/:id` | Gestión, con pestañas: candidatos · jugadores · agenda · sesiones · peticiones · archivos · estado. Cada persona tiene «Ver ficha»: un modal corto con usuario de Discord, nombre y mesas terminadas (#285) |
| **Admin** | `/admin` | **Home del contexto** (#270): una bienvenida, sin métricas hasta que se decida cuáles le sirven a un admin. Es donde cae un admin al entrar y el primer ítem de la nav («Inicio») |
| | `/admin/queue` | Bandeja compartida con reserva (#100): **solo lo que pide una acción**, no un listado de consulta (#176) |
| | `/admin/tables` | **Todas** las mesas, en cualquier estado, con filtros y buscador: el listado de administración, no una cola (#176). La revisión se resuelve en la bandeja o en el aviso de estado de la mesa (#286, #287). Cada fila abre su mesa por el nombre y por el ícono del ojo (#284) |
| | `/admin/tables/:id` | **La mesa entera** (#284, #45): las mismas pestañas del master en solo lectura —candidatos · jugadores · agenda · sesiones · peticiones · archivos—, más **Detalles** primero e **Historial** al final. **Arriba de las pestañas, el aviso de estado** (#287) dice en qué situación está la mesa y trae todas las decisiones de admin: aprobar o pedir cambios en revisión, cancelar, asignar, dar de baja, pausar, reanudar. «Editar mesa» en la cabecera mientras no esté cerrada |
| | `/admin/tables/:id/edit` | **Editar una mesa ajena** (#284): el formulario del master en modo admin. En cualquier estado salvo terminada, cancelada o dada de baja; el cupo no baja de los jugadores que hay, y **sus masters reciben un aviso** |
| | `/admin/catalogs` | Sistemas, tags y plataformas, **un grupo por fila** (#275). La cabecera crea un valor del catálogo de la pestaña, que nace aceptado (#280): el principal o una propuesta sin clasificar, que se aprueba o rechaza desde la fila. Buscar un equivalente encuentra su grupo |
| | `/admin/catalogs/:kind/:id` | **El lienzo de un grupo** (#275): el principal al centro, sus equivalentes alrededor y las propuestas flotando hasta que se conectan. Conectar acepta, mueve (#276) o fusiona; quitar una conexión separa. Otros grupos se traen al lienzo y quedan en `?with=`. Debajo de `md`, una lista con el mismo menú |
| | `/admin/files` | **La biblioteca de la plataforma**, no la personal (#237): lo publicado y lo que espera (#282). Cada fila dice dónde está —**Sin publicar**, **Publicado**, **Oculto**— y sus acciones siguen a ese estado: publicar y cambiar qué es mientras no está publicado, ocultar cuando sí, dar de baja siempre; las tres primeras y la baja se confirman nombrando qué les pasa a los masters. Cada archivo con quien lo subió y en cuántas mesas se usa. Los archivos privados de las personas no aparecen: se alcanzan personificando. **No es `/owner/storage`**: acá solo se marca, los bytes los libera el owner y eso es F6 (#66, #207, #250) |
| | `/admin/files/upload` | **Subir a la biblioteca**, su propia página (#279): la zona acepta varios archivos y cada uno elige en su fila, con un select, qué es. Al pie, **«Subir sin publicar»** y **«Subir y publicar»** (#282), y los dos se confirman diciendo qué ven los masters. Sin elegir qué es cada uno, ninguno pide confirmar: marca cuántos faltan. Al subir todo vuelve a `/admin/files`; lo que falló queda en la lista |
| | `/admin/moderation` | Comentarios por moderar |
| | `/admin/requests` | Solicitudes de rol, de mesa y generales |
| | `/admin/feedback` | Feedback del sistema |
| | `/admin/users` | Usuarios y bloqueos; desde acá se inicia **"ver como"** (#140). Cada fila abre la ficha de la persona por su usuario de Discord y por un ícono (#285) |
| | `/admin/users/:id` | **La ficha de una persona** (#285): datos, roles y estado con sus acciones (cambiar roles, bloquear), asistencia, todas sus mesas con su relación —cada una abre su detalle— y el historial de lo que hicieron los admins. Se abre también desde cualquier persona de una mesa |
| | `/admin/settings` | Configuración: parámetros de negocio y límites (#141). **Sin «textos»**: desde #197 el backend no escribe ninguna frase que lea una persona, así que esa categoría no se construyó (#263) |
| **Owner** | `/owner/audit` | Trazabilidad de cambios (#92) |
| | `/owner/storage` | Borrado físico de archivos (#66) |
| | `/owner/users/:id/migrate` | Migración de cuenta (#83) |
