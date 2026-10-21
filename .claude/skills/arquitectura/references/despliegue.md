# 8 Configuración y despliegue

> Parte de la skill `arquitectura`. Hasta #292 era `arquitectura` §8: una cita a `arquitectura` §8.1 es `arquitectura` §8.1, y §6.2 es §8.2. Los comandos de arranque están en `CLAUDE.md` y los requisitos locales en `backend/README.md`.

Solo la base de datos vive en Docker. El backend corre nativo (`./mvnw spring-boot:run`), sin contenedor propio ni `Dockerfile` — dockerizarlo alenta el ciclo de guardar-y-ver-el-cambio, que `spring-boot-devtools` resuelve reiniciando en caliente (`entorno-local` §2).

## 8.1 Configuración: `.env`, nunca variables de shell

Cada servicio (`backend/`, `frontend/`) tiene su propio `.env.example`, versionado, con **todas** las variables que ese servicio lee — nombre y para qué sirve, nunca el secreto real. Se copia a `.env` (backend) o `.env.local` (frontend) para tener valores propios; ambos patrones ya estaban en el `.gitignore` de la raíz.

Por qué no alcanza con `export` en el perfil de shell (como sí se usa para las variables de los MCP servers, `entorno-local` §6): esas viven en la máquina de quien las exportó y no se documentan en ningún lado del repo. Cada persona que clona el proyecto — o un cliente probando localmente sus propias credenciales de Discord — tendría que llegar a pedir la lista completa en vez de encontrarla en un archivo. `.env.example` es esa lista, versionada y siempre al día porque vive al lado del código que la usa.

Spring Boot, a diferencia de Vite, no lee `.env` de forma nativa: `backend/pom.xml` agrega `springboot4-dotenv` (scope `runtime`, `optional`) para que los mismos placeholders `${VAR:default}` que ya existían en `application*.yml` resuelvan también desde un `.env` local, sin cambiar una línea de código. En un servidor real las variables se inyectan por el mecanismo del hosting (systemd, la plataforma, lo que sea) — ahí no hace falta ningún `.env`, y la librería simplemente no encuentra el archivo y no hace nada.

## 8.2 Despliegue en producción

**Una VM Linux genérica, sin atarse a un proveedor** (#142). En esta etapa se prueba en una instancia EC2 propia, pero el cliente muy probablemente corre el sistema final en una instancia de Oracle Cloud — así que nada de esto puede depender de un servicio propietario de ninguno de los dos. Para el backend, EC2 y Oracle Compute son la misma cosa: una máquina Linux con Docker, donde corre el jar y, al lado, MySQL.

**Perfil `prod`** (`application-prod.yml`): la misma variable `${DB_URL}` que ya usa `dev` (con su default a `localhost:3306` para no pedir nada en local), pero en `prod` **sin default** — si falta, el arranque falla en vez de conectarse silenciosamente a algo que no es. Mantiene agnóstico si MySQL termina corriendo en la misma instancia o en otro lado, y ambos perfiles comparten el mismo nombre de variable en vez de inventar uno distinto por entorno.

**MySQL en producción**: **dónde corre no está decidido** — la misma instancia, un servicio administrado, o cualquier otra cosa. Por eso la aplicación no trae un compose de producción: el único `docker-compose.yml` del repo levanta la MySQL **de desarrollo**, con sus credenciales locales, y Docker no se usa para nada más. Lo que sí es fijo, venga de donde venga la base: contraseñas reales por variable de entorno, nunca `centraldungeon`/`centraldungeon`, y el puerto cerrado a todo lo que no la necesite.

**Los secretos reales nunca son un archivo `.env` en el repo.** Un solo `backend/.env.example` documenta **las mismas variables** para los dos casos — `DB_URL` incluida, con default local en `application-dev.yml` y sin default en `application-prod.yml` — así que no hay dos listas que puedan desalinearse. El archivo real de producción vive **en el servidor**, fuera de este directorio de trabajo — por ejemplo `/etc/centraldungeon/prod.env` — y lo entrega el sistema operativo, no `spring-dotenv` (que solo mira `.env` en el directorio del proceso; por eso el archivo real ni se llama igual). Un ejemplo con `systemd`:

```ini
# /etc/systemd/system/centraldungeon.service
[Service]
EnvironmentFile=/etc/centraldungeon/prod.env
ExecStart=/usr/bin/java -jar /opt/centraldungeon/backend.jar --spring.profiles.active=prod
Restart=always
User=centraldungeon
```

`/etc/centraldungeon/prod.env` es un ejemplo de cómo un entorno concreto puede entregar esas variables, no la forma en que se decidió desplegar: `systemd` lo convierte en variables de entorno reales antes de arrancar el jar. Otro entorno las entregará a su manera. Lo único que la aplicación exige es que lleguen como variables del sistema y que haya **una sola fuente de verdad por secreto**, no copias que puedan divergir.

Delante de todo esto falta un reverse proxy (TLS, dominio) — no decidido todavía, y fuera del alcance de este repo: es configuración del servidor, no de la aplicación.

`springboot4-dotenv` queda en el jar empaquetado (el plugin de Spring Boot no lo excluye como sí hace con `devtools`), pero es inofensivo: sin un archivo llamado `.env` en el directorio desde donde arranca el proceso, no hace nada. La regla simple que lo mantiene así: **jamás copiar `backend/.env` a un servidor.**
