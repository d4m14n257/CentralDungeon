# CentralDungeon backend

Java 25 (Temurin) + Spring Boot 4.1.1. Versiones fijadas en `docs/arquitectura.md` §1.1.

## Entorno local

Java se gestiona con **SDKMAN** (mismo patrón que nvm para Node) y los contenedores con **colima**.

```bash
# una sola vez
curl -s "https://get.sdkman.io" | bash   # requiere bash >= 4; en macOS: brew install bash primero
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25.0.4-tem
sdk install maven

brew install docker      # solo el cliente, el runtime lo da colima
colima start --cpu 2 --memory 4 --disk 60

# docker compose: brew instala el binario standalone, no el plugin del CLI
mkdir -p ~/.docker/cli-plugins
ln -sfn "$(brew --prefix)/bin/docker-compose" ~/.docker/cli-plugins/docker-compose
```

Verificar: `java -version` (debe decir 25) y `docker info` (debe responder, runtime colima).

## Arrancar la base y la app

```bash
cd backend
cp .env.example .env          # completar con tus propias credenciales (arquitectura.md §6.1)
docker compose up -d          # MySQL 8 en localhost:3306
./mvnw spring-boot:run         # perfil dev por defecto; Flyway aplica V1__baseline.sql + V2__seed.sql
```

`.env` es gitignored y `springboot4-dotenv` lo carga solo — nada que exportar a mano en el shell. Sin Discord real configurado en él, el login por Discord no funciona; para eso está el perfil `test` de más abajo.

Los archivos que sube la gente van a `backend/storage/` (`app.storage.root`), que se crea solo al arrancar y está gitignored: son datos, no código. En producción esa variable apunta a un volumen de verdad, y bajo el perfil `test` a un temporal, para que una corrida de Playwright no deje blobs en el árbol de trabajo.

Contrato publicado en `http://localhost:8080/swagger-ui.html`.

## Tests

```bash
./mvnw test      # unitarios (*Test) — no necesitan Docker
./mvnw verify    # unitarios + integración (*IT, Testcontainers) — necesita colima arriba
```

Los `*IT` usan Testcontainers 2.x contra una MySQL real, separada de la de `docker-compose` (arquitectura.md §2.7). Con **colima** (no Docker Desktop) hacen falta dos variables: una para que Testcontainers encuentre el socket, y otra para desactivar Ryuk — el contenedor de limpieza de Testcontainers falla al intentar montar el socket de colima dentro de sí mismo (`operation not supported`, error conocido de colima, no de este proyecto):

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw verify
```

(Verificar la ruta del socket con `docker context inspect colima` si colima corre bajo otro perfil.)

## e2e (Playwright, desde `frontend/`)

Los tests de `frontend/e2e/` corren contra el backend real, nunca contra mocks (`arquitectura.md` §3.4). Necesitan el perfil `test` activo además de `dev`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,test
```

Ese perfil trae dos cosas, y ninguna se registra fuera de él — en `dev`/producción las rutas no existen (404), sin importar que `SecurityConfig` las deje en la lista `permitAll`:

| Qué | Para qué |
|---|---|
| `POST /api/v1/auth/test-login` (`auth/TestLoginController`) | **Saltea** el handshake de Discord y devuelve un access token. Es el atajo para los tests que necesitan una sesión pero no están probando el login (`registration-flow.spec.ts`). |
| `/test-discord/**` (`auth/TestDiscordController`) | Un **Discord falso** dentro del propio backend. `application-test.yml` apunta ahí las cuatro URIs (authorize, token, user-info y `discord.guilds-uri`), así que el handshake **real** corre entero contra él y `discord-login.spec.ts` puede probar el login de verdad desde el navegador (#143). |

Quién entra en el próximo login es estado del servidor, no un parámetro del link: `POST /test-discord/next-login?discordId=…&username=…&inGuild=true|false` antes de arrancar el flujo. Con `inGuild=false` el backend termina en el redirect de `not_guild_member` con la invitación, que es el otro desenlace del login.

## Nota sobre una falsa alarma de esta etapa

**Si `@SpringBootTest`/`spring-boot:run` tira "Unresolved compilation problems" al resolver un bean `@Component` generado por MapStruct, no es un bug de Spring Boot 4.1.1.** Esta etapa perdió varias horas persiguiendo exactamente ese síntoma hasta encontrar la causa real: el JDT Language Server de la extensión Java de VS Code recompila con ECJ hacia el mismo `target/classes` que usa Maven, y si ambos corren a la vez el build de Maven queda pisado a mitad de camino. La firma es siempre esa frase exacta de ECJ. Antes de investigar cualquier otra cosa: pausar el proceso (`pgrep -f "redhat.java.*jre.*bin/java"` + `kill -STOP`, `kill -CONT` para reanudarlo) y correr `./mvnw clean compile` de nuevo.

**Hay un segundo disparador, con la misma firma**: correr `./mvnw clean …` mientras un `spring-boot:run` está levantado. El `clean` vacía `target/classes` debajo del proceso vivo, devtools ve el cambio y reinicia contra un directorio a medio escribir — y lo que llegue a rellenar el JDT en esa ventana es lo que termina cargando. El contexto queda muerto y **devtools ya no revive solo**: hay que reiniciar el proceso a mano. Regla simple: bajar el backend antes de cualquier `clean`, o construir a un jar (`./mvnw package` + `java -jar target/*.jar --spring.profiles.active=dev,test`), que una vez armado ya no depende de `target/classes`.

**Y un tercero, que aparece al agregar archivos nuevos**: si el índice del JDT todavía no los tiene, compila igual la clase que los usa, con la referencia sin resolver, y la deja escrita. La firma en ese caso no es la frase de ECJ sino un `ClassNotFoundException` con el **nombre simple, sin paquete** (`ClassNotFoundException: StubDiscordUserResponse`) — un nombre sin FQN en el constant pool es siempre una referencia que el compilador nunca resolvió. Se arregla igual (pausar el JDT + `./mvnw clean compile`); para que no vuelva, "Java: Clean Java Language Server Workspace" en VS Code lo obliga a reindexar. Se comprueba con `javap -classpath target/classes <FQN>`: si las firmas salen con el paquete completo, la clase está bien.

Los 4 mappers de MapStruct (`GameTableMapper`, `RegistrationMapper`, `NotificationMapper`, `UserMapper`) igual quedan registrados como `@Bean` explícitos en `common/config/MapperConfig.java` en vez de con `componentModel = "spring"` — es una desviación deliberada de `arquitectura.md` §2.2 que ya estaba funcionando cuando apareció la causa real de arriba, y no hay motivo para tocarla ahora que sí se sabe qué la disparaba.
