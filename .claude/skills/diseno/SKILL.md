---
name: diseno
description: CentralDungeon's visual system — where every design value comes from (design/build.py → @theme), the named pattern classes and components a screen must use instead of spelling utilities out (page-title, section-label, row-actions, PageHeader…), the component inventory, work lists with icon actions and pagination, the four mandatory states and the responsive rules. Use before writing the JSX of any screen or component in frontend/, before adding a style, a color or a class, and whenever code or decisiones.md cites "diseno §n".
---

# Diseño

El sistema visual de CentralDungeon. **Es la fuente de todo lo visual** desde #273: antes estaba repartido entre `docs/frontend-diseno.md` y lo que cada pantalla escribía por su cuenta, y eso fue lo que produjo veinticinco copias del mismo título. Conserva la numeración que tenía en `frontend-diseno.md` (§3 y §5), así las citas viejas siguen encontrando su sección. Lo que el documento conserva —principios, navegación, sitemap y wireframes— se lee allá; **cómo se escribe el código**, en la skill `arquitectura`.

## Reglas fijas

1. **Todo valor de diseño sale de `design/build.py`**: paleta, tipografía, espaciados y radios (#130). Se corre `python3 design/build.py`, que mide los 30 pares de contraste y sale con código 1 si alguno cae bajo WCAG AA, y su `theme.css` se transcribe al `@theme` de `src/styles/globals.css`, que nunca se edita a mano (#118). → §3
2. **Ningún valor suelto** (regla dura 18): nada de `text-[11px]`, `#hex` o `px` inventados. Si falta un valor, se agrega en `build.py`. → §3
3. **Un patrón de estilo tiene nombre** (#273). Una combinación de utilidades que cumple un papel —título de página, etiqueta de sección, acciones de fila, lista con divisores, error en un diálogo— **no se escribe literal en una pantalla**: es una clase de `styles/base.css` o un componente de `components/`. **Antes de escribir el JSX se mira el catálogo**; y si el papel aparece por segunda vez, se crea el nombre y las dos pantallas pasan a usarlo. → §5.c
4. **Se reusa antes de crear** (#261). El inventario dice qué existe; un décimo badge de estado o un segundo diálogo con motivo obligatorio es un bug de diseño. → §5
5. **Las acciones de una fila o de una ficha son `IconAction`** (#272): el ícono, un tooltip con el nombre y el mismo nombre como `aria-label`. Van **en una sola línea, sin wrap** (`.row-actions`, que pone `DataTable` por la pantalla). Las destructivas llevan `text-destructive`. El ícono de cada acción sale del **vocabulario fijo**, no se inventa por pantalla. → §5, «Listas de trabajo»
6. **Una lista de trabajo pagina con `PaginationControls`** (#271): tira numerada, «Ir a…» y tamaño 10 · 25 · 50 · 100 en `?size=`. Una lista de lectura trae más con `LoadMore` (#173). Nunca scroll infinito. → §5
7. **Lo que no se puede hacer no aparece** (principio 2): ni gris ni deshabilitado sin decir por qué. **Lo irreversible se confirma** nombrando su consecuencia (principio 3). → `docs/frontend-diseno.md` §1
8. **Toda pantalla que lee datos cubre los cuatro estados**: cargando (skeleton), vacío (`EmptyState`), error (`ErrorState`, con reintento) y sin permiso (`ForbiddenState`, solo para un recurso concreto: un contexto ajeno redirige, #269). → §5
9. **Funciona a 375 px**, diseñado de menor a mayor con los cortes de Tailwind. Una tabla ancha **deja de ser tabla** en móvil y nunca scrollea en horizontal. → §5.b
10. **El color nunca es el único portador de información**: un estado es punto + etiqueta (`StatusBadge`, #261). Los catorce colores de estado se reparten en nueve familias. → §3
11. **Ningún texto se escribe en el JSX**: todo pasa por `t()`, en `es` y en `en` (#117, #198).
12. **Todo cambio visual se ve en los dos temas** —claro y oscuro— antes de darlo por terminado. → §3

## El detalle, por sección

| § | Qué cubre | Archivo |
|---|---|---|
| 3 | Dirección visual, cómo se trabaja la paleta y su contraste, tema claro y oscuro, idioma, colores de estado, karma | [`references/sistema.md`](references/sistema.md) |
| 5 | Primitivas shadcn y las tres que se apartan del default, compuestos con y sin dominio, inventario completo, **listas de trabajo**, hooks compartidos, estados obligatorios | [`references/componentes.md`](references/componentes.md) |
| 5.b | Responsive: cortes, la regla de la tabla ancha | [`references/componentes.md`](references/componentes.md) |
| 5.c | **Clases y componentes de patrón**: la regla, clase o componente, cuándo se crea uno, el catálogo | [`references/patrones.md`](references/patrones.md) |

Las previews de todo esto se generan con `python3 design/build.py` en `design/out/` (ignorado por git): tokens, componentes (`components-*.html`, incluida la sección «Clases de patrón»), estados y las pantallas del sitemap.
