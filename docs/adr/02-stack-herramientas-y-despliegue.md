---
adr: 2
titulo: Stack, herramientas y despliegue
estado: Vigente
actualizada: 2026-10-21
absorbe: [5, 19, 20, 142, 146, 174, 262]
---

# ADR-02: Stack, herramientas y despliegue

> Con qué se construye CentralDungeon, cómo se formatea, cómo se configura en cada entorno y cómo sabe el frontend que hay un backend del otro lado. Las versiones y la configuración las aplica `arquitectura` §1 y §8, con la regla dura 15; esta ADR dice *qué* se decidió y *por qué*.

## §1 Las versiones del stack se fijan explícitamente

**Decisión.** Las versiones viven fijadas en `arquitectura` §1: Java 25 LTS y Spring Boot 4.1.1; React 19.2, Vite 8, TypeScript 5.9, Tailwind 4 y React Router 8. Subir una major es una decisión que se registra en una ADR y actualiza `arquitectura` §1 en el mismo commit (regla dura 15).

**Por qué.** El proyecto estuvo detenido dos años, y arrancar sobre lo vigente en 2024 era empezar con deuda: Spring Boot 3.5 llegó a EOL open source el 30/06/2026 y Java 21 pierde la licencia permisiva de Oracle en septiembre de 2026. Sin código escrito, arrancar en la versión actual costaba cero, mientras que migrar después Jackson 2→3, JUnit 4/5→6 y Testcontainers 1→2 sí cuesta.

*Antes: #19.*

## §2 La UI es shadcn/ui con Tailwind

**Decisión.** shadcn/ui sobre Tailwind 4; MUI se abandona. Lo que genera el CLI de shadcn vive en `components/ui/` y no se edita a mano.

**Por qué.** La mayoría de los componentes se reconstruían igual —varios quedaron a medias en el Next.js viejo—, así que no había costo de migración. Es más liviano que la combinación duplicada MUI + Emotion + styled-components que arrastraba el proyecto, y el MCP `shadcn-ui` da el código fuente real de cada componente.

*Antes: #5.*

## §3 El frontend es TypeScript `strict`, con un espejo escrito a mano por cada `…Response`

**Decisión.** TypeScript con `strict`. Cada `…Response` del backend tiene **su espejo**, una `interface` escrita a mano en el `types.ts` de su feature, con los mismos campos y los mismos nombres; una respuesta que es literalmente un recorte de otra ya espejada puede derivarse de ella (`PublicSession`). Lo que arma el frontend —el payload de una escritura, la vista reducida de una card, el borrador de un formulario— **se deriva** con utility types (`Pick`, `Omit`, `Partial`…) cuando sus campos ya existen en un tipo declarado, y lleva `interface` propia, espejo de su `…Request`, cuando no. Nada que ya exista se re-declara a mano. Nunca `any` (regla dura 6, `arquitectura` §3.2).

**Por qué.** El frontend viejo era JavaScript: TypeScript no se conserva, se introduce. Su valor está en que un cambio de contrato del backend se detecte compilando. El backend no tiene un tipo por entidad: listado y detalle son `record` distintos con campos distintos (`arquitectura` §2.3), y dos respuestas sobre la misma persona pueden contestar preguntas con reglas de visibilidad distintas. Derivar una de otra afirmaría una relación que el backend no garantiza; el espejo uno a uno, en cambio, deja de compilar en cuanto su `record` cambia. Es la forma que ya tenían todas las features. Lo prohibido es lo que de verdad se desincroniza: varias copias a mano de lo mismo que arma el frontend, cada una válida por separado.

*Antes: #20.*

## §4 Prettier formatea el frontend con la configuración del repo

**Decisión.** `.prettierrc.json`: sin punto y coma, comillas simples, ancho 140, coma final. Se corre con `npm run format` y `npm run format:check`. `src/components/ui/` queda excluido. El backend no entra, y `oxlint` sigue siendo el linter: prettier es formato, no reglas.

**Por qué.** Correr prettier sin configuración impuso su default —comillas dobles y punto y coma— sobre un estilo que ya existía de hecho en todo el código. El ancho de 140 sale de medir el código existente: el percentil 98 de las líneas estaba en 114 y las envolturas manuales ya rondaban ese ancho. `components/ui/` se excluye porque reformatearlo sería pelearse con el generador en cada componente nuevo.

*Antes: #174.*

## §5 Un solo `.env.example`, y la topología de producción queda sin decidir

**Decisión.** En dev, los secretos van en `backend/.env` (gitignored), que `springboot4-dotenv` carga sobre los placeholders `${VAR:default}` de `application*.yml`. Hay **un solo** `backend/.env.example` con las mismas variables para dev y prod, `DB_URL` incluida: con default local en dev y **sin default** en el perfil `prod`, igual que todo secreto y todo valor que depende de dónde corre el sistema (`APP_JWT_SECRET`, las credenciales de Discord, el origen CORS, la URL del frontend, la raíz del almacenamiento); `ProdProfileTest` lo verifica. En producción los secretos llegan como variables del sistema, nunca como un archivo del repo. Docker se usa para una sola cosa, la MySQL de desarrollo: un único `backend/docker-compose.yml`, sin compose de producción ni `Dockerfile` de la aplicación; el jar corre nativo. `arquitectura` §8.2 enuncia lo que la aplicación exige de cualquier entorno, y el `systemd` que muestra es un ejemplo. **El destino de producción —instancia tipo EC2 u Oracle Compute, ECS, Kubernetes u otro— queda abierto** hasta que haya uno real, y nada usa un servicio propietario de un proveedor.

**Por qué.** `export` en el perfil de shell no se documenta en ningún lado: cada clon nuevo tendría que pedir la lista de variables en vez de leerla en `.env.example`. Dos listas que documentan lo mismo con nombres distintos divergen sin que nadie lo note, y por eso hay una sola. Un compose de producción no es una duplicación inofensiva: decide la topología por adelantado —MySQL colocada en el mismo host—, y si el destino resulta otro, el archivo y quien lo cite mienten. El compose de dev tampoco sirve para producción: lleva credenciales de desarrollo versionadas y publica el 3306 en todas las interfaces.

*Antes: #142, #262.*

## §6 Un health check público, y la conectividad se ve siempre en la interfaz

**Decisión.** `GET /api/v1/health`, feature `com.centraldungeon.health` sin repository, público en `SecurityConfig` porque `/login` lo consulta sin sesión. Devuelve `200 {"status":"UP"}` mientras el proceso conteste; **no** mide dependencias: responde «¿hay un backend ahí?», no «¿está todo sano?». En el frontend, `useBackendStatus()` sondea cada 15 s con `retry: false`; `BackendStatusIndicator`, montado en `RootLayout`, se ve en toda pantalla —incluida `/login`— y en los dos estados, con los tokens de estado existentes; «Entrar con Discord» se deshabilita con el motivo cuando no hay backend; y un `MutationCache.onError` global en `queryClient` convierte cualquier mutación sin manejo propio en un toast.

**Por qué.** Un login fallido por conectividad era indistinguible de un problema de cuenta, y ninguna mutación tenía `onError`: si fallaba, el botón dejaba de estar *pending* y no pasaba nada más. `retry: false` para no demorar el aviso. El indicador se ve también cuando todo está bien porque así lo pidió quien lo usa para depurar la conexión.

*Antes: #146.*
