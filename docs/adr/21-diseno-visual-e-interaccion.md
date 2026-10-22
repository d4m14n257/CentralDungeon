---
adr: 21
titulo: Diseño visual e interacción
estado: Vigente
actualizada: 2026-10-21
absorbe: [109, 118, 130, 131, 132, 138, 139, 144, 145, 214, 215, 218, 224, 261, 271, 272, 273, 277, 280, 281, 283, 286, 287, 288]
---

# ADR-21: Diseño visual e interacción

> De dónde sale cada valor visual, cuál es la identidad de la marca, y las formas fijas de la interacción: patrones con nombre, cabeceras, avisos de estado, confirmaciones, listas de trabajo, responsive y estados obligatorios. La skill `diseno` lo aplica como reglas (sistema en §3, componentes en §5, responsive en §5.b, patrones en §5.c). La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Manda el diseño, que vive en `design/build.py` y Claude Design, y el JSX solo usa sus tokens

**Decisión.** Colores, tipografía, espaciados y radios se deciden en el sistema de diseño, no en el JSX. El sistema se construye en `design/build.py` como previews HTML + Tailwind, se publica en un proyecto de Claude Design (`claude.ai/design`) con `DesignSync` y se revisa en el navegador; su `theme.css` se transcribe al `@theme` de `styles/globals.css`, que nunca se edita a mano. `build.py` mide 34 pares de texto (AA, 4.5:1) y 6 gráficos no textuales (3:1, ADR-12 §7) y sale con código 1 si alguno cae bajo WCAG AA. Si falta un valor, primero se agrega al diseño. En el JSX: clases de Tailwind que refieren tokens del tema, variantes con `class-variance-authority` y composición con `cn()`; sin CSS-in-JS, sin CSS por componente, sin objetos de estilo y sin valores arbitrarios —un `bg-[#7c3aed]` es la señal de que falta un token—. Figma no está en `.mcp.json` (ADR-01 §9).

**Por qué.** Sin una dirección declarada, cada valor suelto crea una divergencia que nadie sabe de qué lado corregir. Figma se descartó porque su MCP exige un asiento pago y, sin él, es un dibujo del que se copian valores a mano: la divergencia que esto evita. Claude Design gana porque su artefacto ya está hecho del material del código, HTML y clases de Tailwind, y la transcripción es mecánica. Se pierde el lienzo vectorial y se acepta: lo que queda por decidir se juzga mejor sobre el componente real. Se descartaron Penpot (sin integración verificada) y diseñar directo en el código (invierte la dirección). Tailwind con `cva` es además el patrón que shadcn/ui usa por dentro: no hay dos formas de hacer lo mismo.

*Antes: #109, #118, #130.*

## §2 Fantasía sobria y oscura, con la marca fría de la comunidad y el violeta como acento

**Decisión.** Dirección **fantasía sobria**, tema **oscuro por defecto**, densidad **media**; serif solo en títulos (Spectral) y sans en el cuerpo (Inter). La marca es fría: azul profundo (~218°) y violeta índigo (~250°) sobre negro-azulado. El acento de interfaz es el **violeta de la escala que ancla `#3e308b`** (el del logo): 500 como relleno en oscuro, 600 en claro, y el canvas oscuro es `#070c12`. Los roles no se mezclan: el acento aparece como relleno sólido (botones, foco) o como texto con su token propio (§3); los estados, solo como relleno suave con punto y etiqueta. Las tipografías del sitio de la comunidad (Dosis, Abel, Handlee) no se adoptan.

**Por qué.** *Utilitario neutro* no daba identidad a una comunidad que la tiene; *temático marcado* (pergamino, texturas) ahoga los catorce colores de estado, que tienen que distinguirse en toda pantalla. Oscuro porque la comunidad juega de noche. La paleta es una **medición**, no un gusto: el CSS de `links.centraldungeon.org` (`linear-gradient(-45deg, #214b90, #070c12, #211949, #3e308b)`) y los píxeles del logo coinciden en dos familias, ~218° con el 27% de lo cromático y ~250° con el 17%, y ningún píxel cálido. Los dos hues ya los ocupan estados —`state-active` (InProgress) es azul y `state-paused` (Pause), violeta—; se eligió el violeta porque el azul choca con el estado más frecuente y el violeta con uno excepcional, y la separación de roles contiene el choque residual. Dosis en peso 200 sirve para seis links, no para tablas ni formularios.

*Antes: #131, #132.*

## §3 El shell sale del design system: fuentes autohospedadas, acento como texto, gradiente, tema y chip

**Decisión.** Las tipografías se **autohospedan** con `@fontsource-variable/inter` y `@fontsource/spectral` (500/600/700), importadas en `main.tsx`. **`--color-brand-fg`** es el acento como texto —`brand-400` en oscuro, `brand-700` en claro— y lo usan el wordmark y el karma; `build.py` mide sus pares en los dos temas. **`--gradient-brand`** es el gradiente medido de la comunidad (§2) y solo lo usa `/login`, que por eso queda fuera de `PublicLayout` con su propio encuadre a sangre. **El tema se cambia en el `UserMenu`**, con `enableSystem={false}`, y el ítem nombra la acción («Tema claro» estando en oscuro). **El chip de contexto se muestra aunque haya un solo contexto**, sin caret ni menú.

**Por qué.** Las fuentes no se cargaban: la app se veía bien solo en una máquina que ya las tuviera instaladas; autohospedar evita una request bloqueante a un tercero y funciona sin red. Ningún tono único de acento sirve como texto en los dos temas —`brand-400` da 5.71:1 en oscuro pero 3.29:1 en claro, y `brand-500` 3.74:1 en oscuro—, así que el que cambia tiene que ser un token. El gradiente es token para no copiar cuatro hex en el JSX. «Oscuro por defecto» es una decisión de diseño, no la preferencia del sistema operativo. Sin chip, el header no daba ninguna señal del contexto y se sentía roto; el caret se va porque un chip sin menú que parece desplegable promete una interacción que no existe.

*Antes: #144, #145.*

## §4 Los tokens que shadcn espera se resuelven en el design system, no pantalla por pantalla

**Decisión.** `--color-accent`, la superficie de hover y selección de shadcn, vale **`var(--color-border-strong)`** en `design/build.py`, no `var(--color-raised)`. `styles/base.css` repone `cursor: pointer` en todo `<button>` no deshabilitado, que Tailwind 4 sacó de su preflight. Y tres reglas **sin `@layer`**: dos sobre los `data-slot` de shadcn —cursor en los ítems de menú y `border-strong` en el separador. Van sin capa a propósito: tienen que ganarle a clases que trae el componente generado, que no se edita a mano.

**Por qué.** En oscuro `accent`, `raised`, `popover` y `border` valían todos el mismo tono, y en claro `accent` y `popover` también coincidían: el hover pintaba el mismo fondo sobre el que estaba, el separador no existía y el avatar no parecía clickeable. Ya se había parcheado fila por fila en las notificaciones (ADR-18 §4); arreglarlo en el token da el mismo salto en los dos temas —1.32:1 en oscuro y 1.36:1 en claro sobre una superficie elevada—, que es lo que un parche por pantalla no garantiza.

*Antes: #224.*

## §5 Un patrón de estilo tiene nombre, y toda cabecera es `PageHeader` con un lugar fijo para cada cosa

**Decisión.** Una combinación de utilidades que cumple un papel de diseño no se escribe literal en una pantalla: se define una vez. Si es solo estilo, es una clase de `styles/base.css` en `@layer components` con `@apply` sobre los tokens (`base.css` abre con `@reference "./globals.css"`); si tiene estructura, props o comportamiento, un componente de `components/`. Se crea **a la segunda aparición** y las pantallas pasan a usarlo; el catálogo es `diseno` §5.c. **Toda pantalla usa `PageHeader`**, que recibe objetos con nombre y no nodos: `back` (enlace chico arriba del título), `help` (el id de la sección; pinta «Cómo funciona»), `action` (**la** acción principal: violeta, tamaño normal, contra el borde) y `badge` (el estado, junto al título). Desde `sm` los botones van a la derecha; debajo, bajo el título. `SectionHeader` es lo mismo en chico para secciones y pestañas. La ayuda de una frase junto a un control sigue siendo `HelpLink`. Las cabeceras que no son `PageHeader` son la de un perfil, cuyo título vive en `ProfileCard`, y las de las pantallas públicas (`/login`, `/onboarding`, el 404), que no tienen shell.

**Por qué.** Cambiar las acciones de fila en una tabla no se veía en las demás, porque cada pantalla escribía su propio `div`; el título de página estaba escrito a mano 25 veces. Clase y no componente para lo que es solo estilo: envolver un `h1` para ponerle tres clases es una capa sin trabajo. `@layer components`, para que una utilidad escrita al lado siga ganando. La cabecera aceptaba `actions: ReactNode` y la acción principal salía de cinco formas; con objetos no hay manera de armarla distinto. «Volver» va arriba porque a la derecha se leía como la acción.

*Antes: #273, #280, #281.*

## §6 El badge de estado es uno solo, en `components/`; el diálogo con motivo obligatorio no sube

**Decisión.** `StatusBadge`, sin dominio, en `components/`: recibe **la etiqueta ya traducida y un `tone`** —una de las nueve familias, cuyas clases viven en `StatusBadge`—; cada feature conserva su `Record<Estado, StatusTone>` y su `t()`. **No** hay un «diálogo con motivo» compartido: los que existen comparten `FormDialog`, que es el envoltorio. El inventario de compuestos de `diseno` §5 es **completo**, no curado.

**Por qué.** El badge estaba escrito diez veces, idéntico salvo el namespace de i18n, el prefijo de la clave y el mapa a tokens; el umbral es de dos usos (ADR-19 §1). Los diálogos con motivo, en cambio, solo se parecen: lo que queda distinto en cada uno —la mutación, el éxito, los campos alrededor del motivo, el endpoint según quién lee— es lógica de feature, y la misma regla dice que no sube lo que solo se parece. La causa de fondo era el inventario: nombraba dos de los diez badges, así que cada fase leía «un badge es cosa de cada feature» y escribía el siguiente. Un inventario incompleto es el mecanismo por el que el duplicado se reproduce.

*Antes: #261.*

## §7 Todo funciona en teléfono, y la página no se sale de la pantalla en ningún ancho

**Decisión.** Responsive completo: todas las rutas funcionan en teléfono, tablet y escritorio, con los cortes de Tailwind sin inventar ninguno (`md` 768, `lg` 1024, `xl` 1280) y de menor a mayor. **Una tabla ancha deja de ser tabla en móvil**: cada fila es una ficha con identidad y estado arriba, el resto como texto y la acción al pie. **Nunca scroll horizontal.** Los diálogos siguen siendo el `dialog` centrado también en móvil: no hay versión *sheet*. Por arriba, el header y el `main` se acotan a `max-w-5xl` y se centran; por abajo, hasta 320 px, la navegación de secciones es `SectionNav`, que **baja de línea**, y el header achica espacios y **cede el logo** (`min-w-0 truncate`) antes que el selector, la campana o el avatar. Se verifica midiendo `scrollWidth` contra `innerWidth`.

**Por qué.** La comunidad vive en Discord y la mayoría entra desde el teléfono. Acotar el móvil al contexto Jugador se descartó: convierte «¿esta pantalla es responsive?» en una pregunta caso por caso que envejece mal. Las tablas de cinco o más columnas (`/admin/catalogs`, `/admin/users`) son el costo identificado, y por eso la regla de tabla a ficha es una sola. Bajar de línea y no scrollear dentro de la barra, porque una sección fuera de la vista es una sección que nadie encuentra. El *sheet* desde abajo no lo tiene ningún diálogo ni lo pidió ninguna pantalla: como regla general solo producía deuda. Se construye para el diálogo concreto que lo necesite, cuando lo necesite.

*Antes: #138, #277.*

## §8 Los cuatro estados obligatorios se especifican por arquetipo, no por pantalla

**Decisión.** Cuatro arquetipos —listado, detalle, formulario y dashboard— por cuatro estados —cargando, vacío, error y sin permiso—, y **toda pantalla hereda los de su arquetipo**. Lo único que cada pantalla define es el **texto de su vacío y qué acción ofrece**. Los vacíos que son **buenas noticias** —`/master` y `/admin/queue`, «nada espera tu respuesta»— se escriben como tales. El sin permiso (`ForbiddenState`) es para un recurso concreto; un contexto ajeno redirige (ADR-20 §2).

**Por qué.** El skeleton, el error y el sin permiso son idénticos entre listados: dibujarlos pantalla por pantalla serían decenas de variantes repetidas que envejecen en paralelo y terminan divergiendo. El arquetipo captura lo que se repite y la tabla de textos, lo único que no. Un vacío de buena noticia leído con el vacío genérico parece una pantalla rota.

*Antes: #139.*

## §9 Todo lo que crea, cambia o borra se confirma con una revisión que dice qué cambia, a quién afecta y qué sigue

**Decisión.** Antes de mutar, `useConfirm` abre una revisión con el título en pregunta, la consecuencia escrita y el texto del botón de la acción: en **todo formulario y diálogo que escribe** y en las acciones de un clic o gesto (conectar en el lienzo, aceptar como grupo, restaurar desde un menú). `JustifiedTableActionDialog` tiene la prop **`review` obligatoria**. Postularse ya tiene su paso de revisión y no lleva otro. La confirmación se apila sobre el formulario abierto. **Excepciones**: lo que no cambia datos de nadie (descargar, cerrar sesión, marcar como leída al abrir una notificación, el panel de desarrollo) y lo de bajo riesgo que se deshace con el mismo botón y se repite mucho (marcar todo como leído, la asistencia jugador por jugador, tomar y soltar en la bandeja); no se amplían sin decisión. Los tests aceptan la revisión con `acceptReview()` y no la esperan donde el formulario rechaza el valor por validación.

**Por qué.** El principio 3 decía «lo irreversible» y cada pantalla decidía sola: borrar confirmaba y crear casi nunca. El usuario pidió revisión en todo formulario —no alcanza con que llenar y enviar sea deliberado— y dejar fuera lo de bajo riesgo repetido. Un `useConfirm` por pantalla y no un `FormDialog` que confirme solo, porque lo útil de la revisión es nombrar a la persona, la mesa, el valor y la consecuencia, y solo quien dispara la acción lo sabe; una confirmación genérica vuelve al «¿Estás seguro?».

*Antes: #283.*

## §10 Las decisiones sobre una entidad van en su aviso de estado, con las destructivas aparte

**Decisión.** `StatusNotice` (`components/`, sobre `Alert`): un título que nombra la situación, una frase y los botones **con texto en una sola fila**, debajo de la cabecera, visible desde todas las pestañas y **en todos los estados**, también los que no tienen nada que decidir. Nunca una pestaña o sección aparte cuyo único contenido sea un botón. **Las destructivas van en `destructiveActions`, en el extremo derecho, cuando hay otros botones**; si son el único botón, en su lugar normal, a la izquierda. En `/admin/tables/:id` es `AdminTableStatusNotice`, con título y frase por cada estado y todas las decisiones: aprobar, pedir cambios (motivo obligatorio, que el master lee en el historial), cancelar (motivo obligatorio, desde cualquier estado cancelable salvo `Unassigned`, que se da de baja), asignar, dar de baja, pausar y reanudar. Usan los mismos endpoints y la misma reserva que la bandeja (ADR-16 §5); un pedido de pausa del master se sigue resolviendo en la bandeja, porque es un `approval_request`. La lectura es su propia pestaña, **Historial** (`/admin/tables/:id/history`). **El master tiene el mismo patrón**: `MasterTableStatusNotice` en `/master/tables/:id`, con título y frase por cada estado y sus decisiones —enviar a revisión, reenviar, empezar, terminar, pedir pausa, cancelar, borrar—; todas son del `Primary` salvo pedir la pausa, que es de cualquier master. Su historial es la pestaña **Historial** (`/master/tables/:id/history`). En la lista, las acciones de fila siguen siendo íconos (§11), y editar sigue siendo la acción de la cabecera.

**Por qué.** Decidir pide haber leído la mesa, así que va en el detalle y no en la fila. ADR-16 §7 había sacado la revisión del detalle para que dos pantallas no ofrecieran la misma decisión con reglas distintas; acá las reglas son las mismas. Una pestaña de estados para un único botón se ve mal y no se encuentra; mostrar el aviso en todos los estados enseña dónde mirar. La confirmación (§9) frena el error; la distancia evita llegar a él, y un botón solo en el extremo parece perdido. Al master le pasaba lo mismo: una pestaña que mezclaba decidir con leer, con las decisiones al final de la última pestaña.

*Antes: #286, #287, #288.*

## §11 Una lista de trabajo actúa con íconos por fila y pagina con tira numerada, salto y tamaño

**Decisión.** Cada acción de fila es un `IconAction`: ícono de `lucide-react`, **tooltip con el nombre** y el mismo texto como `aria-label`; las destructivas con `text-destructive`. **El vocabulario de íconos es fijo y vive en `diseno` §5**: una acción que ya tiene su gemela usa el mismo ícono. Lo que no se puede hacer no aparece; los botones que no son de una fila siguen con texto. `PaginationControls` muestra «Página X de Y · N en total», anterior/siguiente, una **tira numerada** (la primera, la última y la actual con sus vecinas, `…` en los huecos) y **«Ir a…»** acotado a `1..Y`. Las pantallas de admin suman **«Por página» 10 · 25 · 50 · 100** (default 25) en **`?size=`** como la página (ADR-12 §7); un tamaño fuera de los ofrecidos cae al default y cambiarlo vuelve a la página 1. Con selector, la paginación se ve mientras haya más filas que el tamaño más chico. Debajo de `sm` la tira se esconde.

**Por qué.** Varios botones con texto por fila ocupan una columna entera y compiten con los datos; el `aria-label` existe porque en un teléfono no hay hover. Con mil usuarios, anterior/siguiente obliga a cientos de clics; el salto tiene sentido porque todo endpoint paginado ordena fijo con desempate por `id` (ADR-04 §6). El 100 es el tope del servidor: ofrecer más sería un tamaño que el backend achica en silencio. `/my/files` tiene tamaño fijo (ADR-14 §6).

*Antes: #271, #272.*

## §12 La edición de una mesa es una página con secciones, hermana del detalle

**Decisión.** `/master/tables/:id/edit` es una ruta hermana del detalle —como `/master/tables/new` lo es de la lista— y presenta los campos del wizard **en una sola página con cuatro secciones**, no en cuatro pasos. «Editar» aparece solo en `Draft` y `ChangesRequested`, que es donde el backend acepta el `PUT`.

**Por qué.** Los pasos del wizard existen para no pedir quince valores de golpe a quien no decidió ninguno; quien corrige una mesa ya los tiene y necesita ver de una qué cambia. El `PUT` reemplaza la mesa entera (ADR-04 §7), así que la pantalla que lo alimenta la muestra entera. Hermana y no pestaña, porque las pestañas son vistas de la mesa y su navegación no sirve mientras se edita. Un botón que aparece cuando no puede funcionar es peor que no tenerlo (principio 2).

*Antes: #218.*

## §13 Los componentes compartidos se arreglan en su envoltorio, y lo editable se anuncia por su rol

**Decisión.** `FormDialog` **acota su alto y hace scroll adentro**. El `contenteditable` de TipTap lleva **`role="textbox"` y `aria-multiline`** explícitos. Las dos correcciones van en componentes propios, nunca en `components/ui/`, que genera el CLI de shadcn.

**Por qué.** `DialogContent` se centra con `translate` y no acota alto ni overflow: un formulario más alto que la ventana se derramaba y su mitad de arriba era inalcanzable, porque lo que scrolleaba era la página de atrás. Chrome expone el editor como un grupo cualquiera, así que un lector de pantalla no anunciaba que se podía escribir, y sin `aria-multiline` lo leía como campo de una línea, donde Enter es «enviar». Si un test no encuentra un control por su rol, una persona con lector de pantalla tampoco.

*Antes: #214, #215.*
