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
docker compose up -d          # MySQL 8 en localhost:3306
./mvnw spring-boot:run         # perfil dev por defecto; Flyway aplica V1__baseline.sql + V2__seed.sql
```

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

Los tests de `frontend/e2e/` corren contra el backend real, nunca contra mocks (`arquitectura.md` §3.4). Necesitan el perfil `test` activo además de `dev`, porque ese perfil expone `POST /api/v1/auth/test-login` (`auth/TestLoginController.java`) — el reemplazo del ida-y-vuelta real con Discord mientras no haya credenciales cargadas. Ese controller no se registra fuera del perfil `test`, así que en `dev`/producción la ruta no existe (404), sin importar que `SecurityConfig` la deje en la lista `permitAll`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,test
```

## Nota sobre una falsa alarma de esta etapa

**Si `@SpringBootTest`/`spring-boot:run` tira "Unresolved compilation problems" al resolver un bean `@Component` generado por MapStruct, no es un bug de Spring Boot 4.1.1.** Esta etapa perdió varias horas persiguiendo exactamente ese síntoma hasta encontrar la causa real: el JDT Language Server de la extensión Java de VS Code recompila con ECJ hacia el mismo `target/classes` que usa Maven, y si ambos corren a la vez el build de Maven queda pisado a mitad de camino. La firma es siempre esa frase exacta de ECJ. Antes de investigar cualquier otra cosa: pausar el proceso (`pgrep -f "redhat.java.*jre.*bin/java"` + `kill -STOP`, `kill -CONT` para reanudarlo) y correr `./mvnw clean compile` de nuevo.

Los 4 mappers de MapStruct (`GameTableMapper`, `RegistrationMapper`, `NotificationMapper`, `UserMapper`) igual quedan registrados como `@Bean` explícitos en `common/config/MapperConfig.java` en vez de con `componentModel = "spring"` — es una desviación deliberada de `arquitectura.md` §2.2 que ya estaba funcionando cuando apareció la causa real de arriba, y no hay motivo para tocarla ahora que sí se sabe qué la disparaba.
