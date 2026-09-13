# F4 — Revisión: implementación

> **Cómo se revisa lo construido.** F4 no entrega producto: entrega **saber qué hay, quién lo alcanza y qué está roto**, con nombre y apellido.
>
> Nace de #250. `plan-desarrollo.md` §4 dice qué cubre; acá está el detalle de las cinco rebanadas y el instrumento de cada una.
>
> **Documento vivo mientras F4 esté abierta.** Cada rebanada se marca terminada acá con sus hallazgos y su triaje.

## 1. Por qué existe esta fase

F1.7 intentó la revisión por fase y funcionó: encontró dos huecos de navegación reales y los corrigió. Pero encontró **lo que podía encontrar**, porque revisó F1 contra F1.

Lo que de verdad se rompe está en las costuras entre actores, y **una costura no se puede mirar hasta que existen sus dos lados**:

- **La matriz de roles** no tiene respuesta hasta que los cuatro roles están construidos. Revisarla en F1 es revisar un tercio y darla por buena.
- **Una prueba de IDOR necesita dos actores con derechos distintos.** Con un solo rol construido no hay contra quién probar.
- **El veto de F3.4 excluye al vetado de seis vías de lectura**, y una de ellas —los archivos— la anotó #206 durante F1.4, meses antes de que el veto existiera.
- **El mapa de la interfaz completo** no existe hasta que existen todas las pantallas. Un huérfano en F1 podía ser «alcance de F2 anotado a propósito»; en F4 ya no hay fase siguiente que lo justifique.

Y hay un recurso escaso que es **la atención del cliente**. Gastarla tres veces sobre tres productos parciales, y otra vez sobre el entero, desperdicia justamente las pasadas que importan. F4 la concentra en una.

**Lo que F4 no es.** No es «escribir los tests que las fases no escribieron». Toda regla de negocio llegó con su test unitario escrito por quien la escribió — eso no se movió y no se negocia (`plan-desarrollo.md` §6). F4 escribe el nivel que ningún constructor podía cubrir solo: el que cruza fases, roles y actores.

## 2. Con qué empieza

**No empieza en cero.** Cada fase cerró entregando su **deuda de revisión** —el punto 5 nuevo de `plan-desarrollo.md` §6—, así que F4 arranca con una lista escrita en vez de redescubriendo. Lo primero que hace F4.1 es juntar esas tres listas y confirmarlas contra el repositorio.

Lo que ya se sabe que la espera, relevado hasta hoy:

| Deuda | De dónde viene |
|---|---|
| Los cuatro estados de cada pantalla de F1, F2 y F3, verificados y no asumidos | punto 5 viejo de §6, que #250 movió acá |
| El mapa de navegación completo y su inventario de huérfanos | F1.7 lo hizo para F1; falta el resto |
| `GET /api/v1/files/{fileId}` — solo se consume `/content` | huérfano de F1.7 |
| El buscador de `/admin/tables`, si F3.3 no lo completó | anotado al construir F2.1 |
| La resolución de grupos de catálogo no está acotada | anotado en F2.1 |
| Las dos fuentes de la bandeja que nacen vacías (`comments`, `system_feedback`) | anotado en F3.3 |
| Las tres cosas que se llaman «owner» | anotado en F3 §7 |

## 3. Las cinco rebanadas

F4 no usa el procedimiento de `plan-desarrollo.md` §7 —no hay A1 ni A2, porque no se construye producto—. Lo que sí conserva, y es lo que importa, es el corte: **una rebanada no arranca sin que la anterior tenga sus hallazgos escritos y triados.**

**Cada hallazgo se triaje en uno de tres, siempre, sin una cuarta categoría:**

1. **Bug** — se corrige en F4, con su test de regresión.
2. **Alcance de F5 o F6, anotado a propósito** — con la fase donde vive.
3. **Decisión nueva** — va a `decisiones.md` con su número. Un hallazgo que cambia una regla no es un bug: es una decisión que nadie había tomado.

---

### F4.1 — El mapa de la interfaz

**Por qué primero:** las cuatro que siguen preguntan «¿esto se alcanza?» y «¿desde dónde?». Sin el mapa, cada una lo redescubre por su cuenta.

**Es la rebanada que el cliente pidió por nombre: qué pantallas hay, cómo están conectadas, y qué quedó flotando en el aire.**

**Qué se produce:**

- **El ledger de rutas**: cada ruta del sitemap de `frontend-diseno.md` §2 con cuatro columnas — su guard, **desde qué pantalla se llega navegando** (no escribiendo la URL), qué enlaces salen de ella, y si está construida.
- **El mapa de navegación**, por contexto: Jugador, Master, Admin y las transversales. Dibujado, no listado: lo que se busca son los nodos sin arista de entrada.
- **El inventario de lo que quedó flotando**, en las dos direcciones:
  - **Pantallas sin puerta**: alcanzables solo escribiendo la URL.
  - **Endpoints sin pantalla**: construidos, autorizados, y que ninguna interfaz llama.
  - **Hooks montados en cero lugares.**
  - **Valores de enum que ningún código produce.**
  - **Tipos de notificación que no llevan a ningún lado** — el caso que F1.7 encontró y F2.4 cerró; se vuelve a barrer entero.
  - **Textos de i18n sin usar**, y su inverso: claves usadas que no existen en `en`.
- **Los cuatro estados obligatorios** de cada pantalla —cargando, vacío, error, sin permiso—, verificados uno por uno. Es el punto 5 viejo de §6, hecho de una vez sobre el producto entero.

**El instrumento es un artifact**, como el de F1.7: el ledger es largo y se lee mejor como página que como tabla en markdown. El documento guarda el resumen y los hallazgos; el detalle vive ahí.

**Terminada cuando:** toda ruta del sitemap tiene su fila, todo huérfano tiene su triaje, y ninguna pantalla construida queda sin sus cuatro estados verificados.

---

### F4.2 — La matriz de roles

**Lo que se verifica:** que «quién puede qué» sea lo que los documentos dicen, **probado y no leído de un `@PreAuthorize`**.

- **La matriz completa**: cada endpoint × cada rol. Cuatro roles, más el actor sin sesión, más el actor bloqueado.
- **La matriz de `fase-3-admin-owner.md` §3 termina en una prueba que la recorre.** Una tabla en un documento no impide que alguien escriba `hasRole('ADMIN')` y deje al owner afuera — y eso no se nota en desarrollo, donde el actor de prueba suele ser admin.
- **Pertenencia, que es la otra mitad y la más fácil de olvidar** (#121): el rol correcto sobre el **recurso ajeno**. Un master legítimo pidiendo la mesa de otro master; un jugador pidiendo la postulación de otro; un admin leyendo lo que #45 le permite y lo que #43 no.
- **Los tres contextos no son autorización** (#103, #222): `/player` no exige el rol `Player`, y eso es deliberado. La prueba fija que la interfaz no decide permisos y el backend sí.
- **La exclusión `Admin`/`Owner`** de #169 y las tres invariantes de `fase-3-admin-owner.md` §3, incluida la que dice que la plataforma nunca se queda sin owner.

**Terminada cuando:** existe una suite que recorre la matriz, y cada celda que no coincide con la documentación quedó triada.

---

### F4.3 — Integridad

**Lo que se verifica:** que los datos no puedan quedar en un estado que ninguna pantalla sepa mostrar.

- **Las invariantes que MySQL no sostiene**, todas juntas y con concurrencia real: un solo `Primary` vivo por mesa (#73), una sola postulación activa por par (#28), el cupo y el rechazo automático (#34), la reserva de la bandeja (#100) y la que F3 estrena, que la plataforma no se quede sin `Owner`.
- **Las referencias huérfanas que #78 obliga a vigilar.** La referencia polimórfica de `approval_requests` no tiene FK, así que la integridad es del service — y la verificación periódica que la decisión exige se prueba acá.
- **La coherencia del borrado lógico** (#25), que es transversal y por eso no es de nadie: una fila `Deleted` tiene que ser invisible en **todos** los caminos de lectura, no en los que alguien se acordó. Mesas (#175), postulaciones, vínculos de catálogo (#190), archivos, filas de `masters` (#216).
- **La cadena de claves foráneas que rompió la limpieza del e2e cinco veces** —F1.2 la agenda, F1.3 el calendario, F1.4 los archivos de mesa, F1.5 las entregas y F2.2 los archivos de la postulación, todas en `TestDataService` (#171, #172)—. **Cinco veces es una clase de error que merece una barrera, no una corrección más**, y el motivo es cómo se manifiesta: la limpieza responde `500`, nadie lo mira porque las pruebas ya terminaron, la base se llena, y **la corrida siguiente falla por paginación en un lugar que no tiene nada que ver** — en F2.2 fueron 16 pruebas en rojo y un admin buscando una fila que había quedado fuera de la primera página. Acá se verifica que el orden de borrado cubra el grafo entero, y **se decide la barrera**: el candidato obvio es que la limpieza falle ruidosamente en vez de en silencio, o un test que recorra el grafo de `@Entity` y exija que toda tabla puente esté en la lista.
- **Lo derivado contra lo guardado** (#11, #232): los conteos de jugadores, la asistencia agregada (#137) y los usos de un archivo se derivan y no se cachean. Se comprueba que ninguna ruta haya introducido una copia.

**Terminada cuando:** cada invariante tiene su test de integración con Testcontainers, y el barrido de borrado lógico cubrió cada entidad con `deleted_at`.

---

### F4.4 — Seguridad

**Lo que se verifica:** que lo que no se puede ver, no se vea — y que negar no confirme.

- **IDOR por recurso**, sistemáticamente: para cada entidad con id en la URL, el actor equivocado recibe la respuesta correcta. **Y la respuesta correcta a veces es `404` y no `403`**, que es una regla de este proyecto y no una preferencia: el veto (#29), la mesa borrada (#175) y el perfil caducado (#249) lo niegan sin confirmar que existan.
- **Las vías de lectura de un archivo**, que para F4 son siete y llegaron de a una: propio, publicado, adjunto privado de una mesa que dirigís, compartido por una mesa (#206), entregado a una petición (#211), adjunto de un pedido (#236) y adjunto de una postulación (F2.2). **Cada una se prueba con el actor que no debería pasar**, y con el vetado que F3.4 agregó a todas. El número no se da por bueno: se cuenta contra el Javadoc de `FileService.requireReadable`, que es donde viven de verdad.
- **La lista blanca del sanitizador** (#62, #186) sobre los tres campos de texto enriquecido, al guardar y al servir. Es la superficie de XSS más directa del sistema.
- **El circuito de sesión** (#125, #127): el access token en memoria y nunca en `localStorage`, el refresh rotativo en cookie `httpOnly`, el CSRF activo solo en `/auth/refresh`, el reintento único ante `401`, y que el token de Discord se descarte al terminar el callback.
- **Que el JWT no autorice** (#122): los roles se releen de la base en cada request, y un rol quitado deja de valer dentro de la ventana de la caché de #128 — que el bloqueo de F3.1 tiene que invalidar en el momento.
- **Que ningún `Map<String, Object>` cruce HTTP** (regla dura 3) y que ningún endpoint devuelva más de lo que la pantalla necesita.
- **El doble de login de pruebas** (`TestLoginController`, #143, #223) **no existe fuera del perfil `test`**. Es la verificación más barata de esta rebanada y la más cara de olvidar.

**Fuera de alcance, con su motivo:** el anonimato de los comentarios (#43, #45) no se puede verificar porque los comentarios son **F5**. Queda anotado como la primera línea de la revisión de esa fase.

**Terminada cuando:** cada vía de lectura tiene su prueba negativa, y ninguna negativa devuelve un código que confirme lo que niega.

---

### F4.5 — La revisión mano a mano

**Es la rebanada del cliente, no de un agente.** Las cuatro anteriores producen el instrumento; esta es la pasada humana sobre el producto real.

**Cómo se ejecuta:**

- Se recorre el producto **navegando**, con el mapa de F4.1 al lado, con un actor por rol armado desde el `DevPanel` (#158).
- **Cada hallazgo se anota en el momento**, con la pantalla, lo que se esperaba y lo que pasó. Un hallazgo que se recuerda al final se recuerda mal.
- **Cada hallazgo se triaje en los tres de §3**, uno por uno, sin dejar ninguno «para ver después».
- Lo que se corrige, se corrige **con su test de regresión**: si un humano lo encontró una vez, un test tiene que encontrarlo la próxima.

**Terminada cuando:** el registro de hallazgos está cerrado — cada uno corregido con su commit, o anotado con su fase y su motivo.

## 4. Lo que F4 explícitamente NO hace

| Queda fuera | Por qué |
|---|---|
| Construir pantallas o endpoints nuevos | F4 verifica. Una pantalla que falta es un hallazgo, y su fase es F5 o F6 |
| Escribir los tests unitarios que las fases debían escribir | Si falta uno, es un hallazgo y su triaje es «bug»: la fase no cumplió el punto 2 de §6 |
| Rediseñar | Un cambio de diseño es una decisión (#250, categoría 3), y se toma con su número antes de tocar nada |
| Verificar comentarios, karma y feedback | Son **F5** y todavía no existen |
| Verificar auditoría, tiempo real y «ver como» | Son **F6** |
| Optimizar rendimiento | No está en el alcance de ninguna fase todavía, y meterlo acá lo convertiría en trabajo sin destino |

## 5. Cómo se mide que sirvió

Una fase de revisión que termina sin hallazgos no probó que el producto esté bien: probó que la revisión fue floja. Las tres señales de que F4 hizo su trabajo:

1. **El inventario de huérfanos está vacío o justificado**, ítem por ítem. Cero ítems sin triaje.
2. **La matriz de roles existe como suite y corre en verde**, y cada celda que no coincidía con los documentos terminó en una corrección o en una decisión.
3. **Todo hallazgo corregido tiene su test de regresión.** Es la diferencia entre haber revisado y haber arreglado: sin el test, el mismo bug vuelve en F5.

## 6. Verificación

Se prueba **contra el backend y el frontend que ya están corriendo** — no se levantan instancias paralelas.

```bash
cd backend && ./mvnw test          # unitarios, sin Docker
cd backend && ./mvnw verify        # + Testcontainers (colima arriba)
cd frontend && npx tsc -b          # typecheck strict
cd frontend && npm run test        # Vitest
cd frontend && npm run test:e2e    # Playwright contra el backend real
cd frontend && npm run format      # prettier del repo (#174)
```

**Nunca `./mvnw clean` con el backend levantado**: borra `target/classes` bajo el proceso vivo, el backend se cae, y la suite e2e falla entera como si el código se hubiera roto.
