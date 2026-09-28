---
name: entorno-local
description: How CentralDungeon's local environment behaves and the traps that make a healthy codebase look broken — the user's own running backend and frontend, never mvn clean under a live JVM, VS Code's Java autobuild, and the colima variables Testcontainers needs. Use before running, restarting or verifying anything against the backend, frontend, Playwright or Testcontainers, and before blaming a failing suite on the code.
---

# El entorno local y sus trampas

Cada una de estas cosas costó una sesión entera la primera vez, y **todas se ven igual: como si el código estuviera roto.** Antes de diagnosticar un fallo masivo, se descartan en este orden. Los comandos de siempre están en `CLAUDE.md` y en `backend/README.md`.

## 1. Se usa el backend y el frontend que ya están corriendo

**Nunca se levanta un segundo backend** (otro puerto, como 8081) **ni un Vite descartable** para verificar algo, aunque la intención sea no molestar. Se usa el proceso que ya está arriba: `lsof -i :8080` o `ps`.

- **Por qué:** un backend paralelo arrastra una cadena de overrides —origen CORS, URL del frontend, URIs del proveedor OAuth2— que apuntan todas al puerto equivocado, y cada una pide su propio arreglo. La sesión termina depurando el andamio de diagnóstico en vez de la tarea.
- **Si hay que reiniciar para tomar un cambio**, se mata y se relanza en 8080, o se le pide al usuario. Reiniciar su proceso no le cuesta nada.
- **Spring Boot no toma los cambios solo; Vite sí.** El proceso en 8080 suele ser un `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,test` lanzado por una sesión anterior, y lleva el código que se compiló entonces. Todo cambio de backend exige relanzarlo antes de que Playwright ejercite lo nuevo. Se confirma pegándole a un endpoint nuevo: un `404` en vez de un `401` significa que la ruta existe.

## 2. Nunca `./mvnw clean` con el backend levantado

`clean` borra `target/classes` bajo la JVM viva, el proceso muere, y la corrida siguiente de Playwright falla **entera** con «element not found».

- Se usa `./mvnw test` o `./mvnw verify` sin `clean`. Si de verdad hace falta un build limpio, se avisa antes y se pide reiniciar el backend después.
- Antes de culpar al código por una suite e2e entera en rojo: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/health`. Un `000` significa que el servidor no está.
- **Sin `clean` también puede pasar.** El backend corre con `spring-boot-devtools`, que vigila `target/classes`: `./mvnw test` recompila lo que cambió y devtools **reinicia la app, que vuelve a pasar por Flyway**. Si el reinicio falla, el proceso queda vivo pero sin escuchar en 8080, y la causa está en la terminal del usuario, no en la salida de los tests. En F4.0 fue `Migration checksum mismatch for migration version 2` y `12`: una reescritura masiva de citas había tocado comentarios de dos migraciones aplicadas (ver `modelo-datos`, «Cómo se usa el modelo»). Después de un `./mvnw test` o `verify` con el backend levantado, se chequea el health; si da `000`, se pide el log antes de diagnosticar. Si el proceso corre en la terminal del usuario, **se le pide que lo reinicie**: no se mata un proceso ajeno.

## 3. El autobuild de Java de VS Code corrompe `target/classes`

La extensión de Java de VS Code (`redhat.java`) compila en paralelo con Maven **al mismo directorio**. Si le gana la carrera —sobre todo justo después de un `clean`, cuando reaparecen los `*MapperImpl.java` que genera MapStruct—, recompila esos archivos con un classpath incompleto y pisa los `.class` buenos.

- **Síntoma:** `spring-boot:run` cae con `Unresolved compilation problems` dentro de un `java.lang.Error`, en el `<init>` de un mapper (`UserMapperImpl`, `GameTableMapperImpl`). Parece un error de compilación real; el fuente está bien.
- **Arreglo aplicado:** `.vscode/settings.json` tiene `"java.autobuild.enabled": false`. **Ese archivo no se versiona** (`.vscode/` está en `.gitignore`), así que en una máquina nueva hay que crearlo a mano. Toma efecto recargando la ventana o con «Java: Restart Language Server».
- **Lo que queda:** una JVM que ya corría antes del arreglo puede arrastrar el estado roto y responder `500` genéricos en endpoints sanos. No se cura recompilando: necesita un reinicio real del proceso (§1).

## 4. Testcontainers no encuentra colima solo

`./mvnw verify` falla **todas** las IT con `Could not find a valid Docker environment`, aunque `colima status` diga que corre y `docker info` responda, porque el CLI de Docker usa su *context* y Testcontainers 2.x no lo lee. Hay que exportar dos variables (documentadas también en `backend/README.md`):

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
```

Ryuk va desactivado porque su contenedor de limpieza falla al montar el socket de colima dentro de sí mismo (`operation not supported`): es un problema conocido de colima, no del proyecto.

Para iterar sobre pocas clases sin pagar la corrida completa: `./mvnw verify -Dit.test=ClaseIT,OtraIT`.

## 5. Verificar que algo no existe

Bajo zsh, `ls backend/*.md` sin coincidencias imprime `no matches found` como **error de glob del shell**, y se lee como si el archivo faltara. Antes de afirmar que algo no existe: `git cat-file -e HEAD:<ruta>` o `ls <ruta exacta>`.
