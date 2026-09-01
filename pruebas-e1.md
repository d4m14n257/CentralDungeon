# Cómo probar la Fase 1 (E1)

Nota personal de testing, no es doc oficial del repo (esos viven en `docs/`). Mapa de navegación
y checklist completo de qué revisar: ver el artifact **"Rebanada E1"** que ya tenés publicado.

## Cuentas de prueba por defecto

**Siempre las mismas dos, para no perder de vista con qué cuenta hay datos armados:**

| Cuenta | Discord ID | Qué tiene |
|---|---|---|
| **Jugador** | `jugador-1` | `Player` en "La Cripta de Ondrak", `Rejected` en "Tumbas de Sal" (con justificación), **Secondary master** de "El Pozo de las Sombras" — para probar el doble rol jugador/master |
| **Master** | `master-1` | `Secondary` de "La Cripta de Ondrak", "Tumbas de Sal" y "El Pozo de las Sombras" (`Primary` en esta última) |

Desde ahora, el **panel de desarrollo** (ícono de matraz, abajo a la derecha, solo visible con
`npm run dev` — decisiones.md #158) ya trae estas dos precargadas en sus botones de login rápido:
no hace falta el `fetch()` de consola de la sección 1 salvo que necesites otro `discordId` puntual
(jugador fuera del servidor, cuenta bloqueada — eso sigue siendo la sección 2 y 3, el panel no las
reemplaza). El panel también tiene un botón para crear una mesa de prueba abierta con la cuenta
master actual.

## 0. Arrancar el backend en modo test

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,test
```

Con `test` activo se habilitan `POST /api/v1/auth/test-login` y `/test-discord/**` — ninguno de
los dos existe en `dev` solo (así debe ser en producción). El frontend sigue igual, en
`http://localhost:5173`.

Si en algún momento el backend empieza a tirar `Unresolved compilation problems` o 500 genéricos
sin sentido al arrancar: es el JDT de VS Code pisando `target/classes` (ver memoria del proyecto).
Solución de una vez: `.vscode/settings.json` ya tiene `"java.autobuild.enabled": false` — si
seguís viéndolo, recargá la ventana de VS Code para que tome el cambio, y de última `cd backend
&& ./mvnw clean compile` antes de arrancar.

---

## 1. Entrar rápido como Jugador o Master (para probar el resto de la app)

`test-login` salta Discord del todo. Es un `POST`, así que se corre desde la consola del
navegador (F12 → *Console*) parado en `http://localhost:5173` — no se abre en la barra de
direcciones.

**Como Jugador:**

```js
fetch('http://localhost:8080/api/v1/auth/test-login?discordId=jugador-1&asMaster=false', { method: 'POST', credentials: 'include' })
  .then(() => { location.href = '/' })
```

**Como Master** (te da el rol de una, sin tocar la base a mano):

```js
fetch('http://localhost:8080/api/v1/auth/test-login?discordId=master-1&asMaster=true', { method: 'POST', credentials: 'include' })
  .then(() => { location.href = '/' })
```

Cada `discordId` es una cuenta distinta y persiste — repetí el mismo para volver a esa cuenta.

**Ojo:** `test-login` no revisa el guild ni el `status` del usuario — es un atajo de sesión, no
reproduce el chequeo real. Para eso está la sección 2.

**Ojo también con dónde estás parado antes de correr esto.** `OAuthCallbackPage` mira el
parámetro `?error=` de la URL antes que nada, sin importar si la sesión es válida. Si venís de
probar "jugador que no está en el servidor" (sección 2) y tu URL todavía es algo como
`.../auth/callback?error=not_guild_member`, un `location.reload()` repite esa misma URL con el
error puesto — la sesión nueva queda bien de fondo, pero seguís viendo la pantalla de error vieja.
Por eso el snippet usa `location.href = '/'` en vez de `reload()`: te saca de esa URL en vez de
repetirla.

### Las mesas de prueba compartidas

`master-1` ya es Secondary de las dos, cada una con una postulación pendiente distinta — para no
tener que crear nada nuevo cada vez que se quiere probar candidatos:

| Mesa | Para probar |
|---|---|
| **La Cripta de Ondrak** | Aceptar una postulación (`jugador-1` ya quedó `Player` ahí) |
| **Tumbas de Sal** | Rechazar una postulación (`jugador-1` ya quedó `Rejected`, con justificación) |
| **El Pozo de las Sombras** | Doble rol: `jugador-1` es **Secondary master** acá (y `master-1` es `Primary`) mientras sigue siendo jugador en las otras dos. Sirve para probar que el explorador de Jugador le oculta esta mesa a `jugador-1` (decisiones.md #155) y que el `ContextSwitcher` le ofrece el contexto Master aunque no tenga el rol de plataforma (decisiones.md #135) |

Andá a `/master/tables` logueado como `master-1` y ahí están las tres. Si en algún momento alguna
aparece sin `master-1` asignado, es porque se perdió esa fila de `masters` (pasó una vez, por una
limpieza de datos de prueba mía) — se vuelve a asignar así, sin crear nada nuevo (cambiá el nombre
de la mesa en el `WHERE` según cuál sea):

```bash
docker exec backend-mysql-1 mysql -ucentraldungeon -pcentraldungeon centraldungeon -e "
INSERT INTO masters (game_table_id, user_id, master_type, created_at)
SELECT gt.id, u.id, 'Secondary', NOW()
FROM game_tables gt, users u
WHERE gt.name = 'La Cripta de Ondrak' AND u.discord_id = 'master-1'
AND NOT EXISTS (SELECT 1 FROM masters m WHERE m.game_table_id = gt.id AND m.user_id = u.id);
"
```

### Armar una mesa nueva (si hace falta otra, aparte de la compartida)

Logueado como `master-1`, desde la consola:

```js
fetch('http://localhost:8080/api/v1/game-tables', {
  method: 'POST',
  credentials: 'include',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'Mesa de prueba', description: 'Para probar E1', maxPlayers: 4 }),
})
  .then((r) => r.json())
  .then((table) => fetch(`http://localhost:8080/api/v1/game-tables/${table.id}/open`, { method: 'POST', credentials: 'include' }))
  .then(() => { location.href = '/' })
```

Eso la deja `Opened` — visible en el explorador para que un jugador se postule.

---

## 2. Probar el login real (la pantalla en sí, con el chequeo de guild)

Acá sí importa lo que ve la pantalla, así que hay que clickear de verdad. Primero le decís al
Discord falso quién "llega" (desde una terminal, con `curl` — este si es normal, no de consola
del navegador), después vas al navegador y clickeás.

**Jugador que está en el servidor:**

```bash
curl -s -X POST "http://localhost:8080/test-discord/next-login?discordId=jugador-2&username=Jugador2&inGuild=true"
```

→ `http://localhost:5173/login` → "Entrar con Discord" → onboarding la primera vez, después
directo al explorador.

**Jugador que NO está en el servidor:**

```bash
curl -s -X POST "http://localhost:8080/test-discord/next-login?discordId=outsider-1&username=Outsider&inGuild=false"
```

→ mismo click → "Todavía no sos parte del servidor", con el botón de invitación y el de "Volver
al login".

**Importante:** el estado de `next-login` es uno solo y se pisa. Si corriste el de outsider y
después querés probar el de jugador normal, corré el `curl` de nuevo antes de clickear — el
navegador no sabe cuál corriste último, usa el que haya quedado configurado en el backend.

---

## 3. Probar una cuenta bloqueada

El bloqueo (`status = Blocked`) es un chequeo nuestro, posterior al de guild — así que **sí** se
puede probar completo con el Discord falso, no hace falta Discord real.

1. Logueate una vez normal con un `discordId` de prueba (sección 2) para que el usuario exista en
   la base.
2. Bloquealo:

   ```bash
   docker exec backend-mysql-1 mysql -ucentraldungeon -pcentraldungeon centraldungeon -e "
   UPDATE users SET status='Blocked' WHERE discord_id='jugador-2';
   "
   ```

3. Volvé a configurar el mismo `discordId` como "el que llega" y clickeá "Entrar con Discord" de
   nuevo (no `test-login` — ese no revisa el `status`, te dejaría entrar igual):

   ```bash
   curl -s -X POST "http://localhost:8080/test-discord/next-login?discordId=jugador-2&username=Jugador2&inGuild=true"
   ```

   → "Tu cuenta está bloqueada", sin acción más que volver al login.

4. Para desbloquearlo y seguir probando con esa cuenta:

   ```bash
   docker exec backend-mysql-1 mysql -ucentraldungeon -pcentraldungeon centraldungeon -e "
   UPDATE users SET status='Allowed' WHERE discord_id='jugador-2';
   "
   ```

---

## Notas

- Todos los usuarios y mesas que se crean así son datos reales en tu MySQL local — no hay reset
  automático. Se puede borrar a mano cuando estorben, pero **nunca `jugador-1` ni `master-1`** —
  son las cuentas por defecto con las que el panel de desarrollo arranca y tienen las mesas fijas
  de arriba armadas encima:
  `docker exec backend-mysql-1 mysql -ucentraldungeon -pcentraldungeon centraldungeon -e "DELETE FROM users WHERE discord_id IN ('jugador-2','outsider-1');"`
- Con `dev,test` activo, la suite `frontend/e2e` (`npx playwright test` desde `frontend/`) corre
  contra este mismo backend.
- Para probar el login con tu cuenta **real** de Discord (no simulada), el backend tiene que
  correr sin `test`: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. Ahí `test-login` y
  `/test-discord/**` dejan de existir (404) — es esperado, no un error.
