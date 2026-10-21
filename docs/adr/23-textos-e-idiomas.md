---
adr: 23
titulo: Textos e idiomas
estado: Vigente
actualizada: 2026-10-20
absorbe: [107, 117, 197, 198, 225]
---

# ADR-23: Textos e idiomas

> Dónde nace cada texto que lee una persona y en qué idioma se muestra: i18n desde el primer componente, dos idiomas, y un backend que manda códigos en vez de frases. La skill `arquitectura` §3.3 (i18n) y la regla dura 18 lo aplican; `diseno` §3 cubre el selector de idioma. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Todo texto visible nace en un JSON de idioma, desde el primer componente

**Decisión.** Ningún string visible se escribe en el JSX: todo pasa por `t('espacio.clave')` de `i18next`, con los textos en `locales/<idioma>/<espacio>.json`, un espacio de nombres por feature. **JSON, no XML**, y sin cargador propio. Rige desde la primera etapa, y toda clave nueva entra en los dos idiomas (§2) en el mismo commit.

**Por qué.** JSON es nativo del ecosistema: no necesita parser, se tipa con TypeScript, e `i18next` ya resuelve interpolación, plurales, fallbacks y carga por espacio de nombres, que con un cargador propio habría que escribir a mano. La intención de tener inglés era real (la comunidad es de LatAm y algo de Europa, ADR-03 §6), y hecho desde el principio cuesta una indirección por string, mientras que retrofitearlo obliga a recorrer cada componente escrito. Queda prohibido el término medio que el legacy ya intentó: `english.xml` y `spanish.xml` vacíos, un helper a medio escribir y ningún texto traducido. O todo texto nace en el archivo de idioma, o no se instala nada.

*Antes: #107, #117.*

## §2 Hay dos idiomas, se eligen como el tema, se detectan del navegador y caen en español

**Decisión.** `es` y `en`, los dos en el bundle. `resolveInitialLanguage()` toma la elección guardada en `localStorage`, después `navigator.languages` por subtag primario, y si nada coincide, **español**. El selector vive en el `UserMenu`, junto al tema, y **también en la tarjeta de `/login`**, ahí con los dos idiomas a la vista y no plegados. Cada idioma se nombra a sí mismo («English», nunca «Inglés»). `<html lang>` lo sincroniza `providers/i18n.ts`. Los tests fijan el idioma: Vitest en el setup, Playwright en la config.

**Por qué.** La comunidad escribe y juega en español: un navegador que no se reconoce probablemente es de alguien que lo habla. Se guarda en el dispositivo y no en el modelo, igual que el tema: i18n quedó fuera del modelo a propósito (`arquitectura` §4.7) y meterlo sería columna, migración y endpoint para una preferencia del dispositivo. Cada idioma se nombra en sí mismo porque quien busca el suyo no necesariamente lee el que está viendo. `/login` es la única pantalla sin `UserMenu`, y plegar el control se lo escondería justo a quien no puede leer la pantalla. `<html lang>` vive en el provider para valer también en `/login`. Sin fijar el idioma, una aserción sobre una etiqueta pasaba o fallaba según el locale de la máquina.

*Antes: #198.*

## §3 El backend no escribe ni guarda frases para una persona: manda códigos y parámetros

**Decisión.** Las notificaciones guardan **tipo + parámetros** (`NotificationParams`, columna `params`), no una frase. Los `ApiException` llevan `errorCode` + `errorParams`, y su `message` es inglés para el log, nunca para mostrar. Un motivo automático se guarda como código (`TABLE_FULL` en vez de «Mesa llena»). El frontend arma la oración en el idioma de quien lee. **Lo único verbatim es lo que escribió otra persona**: el motivo de un master se muestra tal cual. `errorParams` es un `Map` y no un `record` con nombre: es la única excepción a la regla dura 3, porque es un *extension member* de RFC 9457, el mecanismo que el estándar define para esto.

**Por qué.** Es lo que hace posibles los dos idiomas. Una fila de `notifications` se escribe una vez y se lee durante meses, y en el medio la persona puede cambiar de idioma: una frase guardada queda congelada en el idioma del día en que se emitió. Lo mismo con el `detail` de un `409`: mostrarlo tal cual era mostrar español a quien eligió inglés. Traducir lo que escribió una persona sería ponerle palabras en la boca. Y un `record` por código de error sería un tipo por mensaje sin reutilizar ninguno.

*Antes: #197.*

## §4 Un valor que trajo la aplicación se traduce por su código; uno que nombró una persona, no

**Decisión.** `table_types` tiene una columna **`code`, nullable y única**. Las filas que siembra la aplicación llevan código (`PUBLIC`, `FIRST_CLASS`), la API lo manda junto al `name`, y el frontend arma nombre y descripción con `t('tableTypes.<CODE>.name')`. **`code IS NULL` significa «esto lo nombró una persona»**, y entonces el `name` se muestra tal cual, como en `systems`, `tags` y `platforms`. El `name` queda además como respaldo para un código que llegue antes que su traducción. Lo que identifica un tipo en el frontend es el `code` cuando lo hay, nunca la etiqueta. El backend sigue sin `MessageSource` ni `Accept-Language`.

**Por qué.** «Public» y «First class» se veían en inglés con la interfaz en español. Responder en el idioma del cliente desde el backend se descartó con el criterio que ya fijó §3: lo que escribió la aplicación sigue el idioma de quien lee, lo que escribió una persona queda verbatim. Ese i18n en el backend tampoco habría resuelto los tipos que agregue un admin, así que el respaldo hacía falta igual. Los otros catálogos no se tocan: `D&D 5e`, `Discord` o `Roll20` son nombres propios, y traducirlos sería el error opuesto. Comparar por etiqueta se rompe en cuanto la etiqueta se traduce.

*Antes: #225.*
