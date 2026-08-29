---
name: nuevo-componente-react
description: Scaffolds a new React component or page using shadcn/ui, Tailwind, and TanStack Query, inside its feature folder, following CentralDungeon's frontend architecture. Use when adding or finishing a component/page in frontend/ (React + Vite).
---

# Nuevo componente/página React

Sigue `docs/arquitectura.md` §3. Léelo si no lo tienes fresco en contexto.

## Reglas fijas

1. **Feature-first, pero las pantallas van aparte.** Un componente de dominio vive en `src/features/<dominio>/` (`auth`, `tables`, `registrations`, `files`, `catalogs`, `comments`, `notifications`, `users`), en `components/`, `api/` o `hooks/`. **Las páginas van en `src/routes/`, nunca dentro de una feature.** Ubicar con el árbol de decisión de §3.1.1: si el nombre o las props mencionan un concepto del dominio, va en la feature aunque parezca reutilizable; si no menciona ninguno, va a la capa transversal de la raíz (`components/`, `hooks/`, `lib/`, `types/`) recién **cuando una segunda feature ya lo necesita** (§3.1.2).
   - **Una feature nunca importa de otra**, sin excepciones. Si una pantalla necesita varios dominios, cada bloque es un `…Section` que vive en su feature, hace su propia query y recibe un **id**, no la entidad (§3.1.5).
   - `components/` de la feature es **plano**, con sufijo (`Form`, `Dialog`, `Section`, `Card`, `Badge`, `List`, `Editor`). Nada de subcarpetas `modals/` o `actions/` (§3.1.3).
   - **Cada feature exporta su superficie pública en `features/<dominio>/index.ts`**; desde afuera se importa `@/features/tables`, nunca una ruta interna. Es el único barrel del proyecto (#114).
   - No se crean carpetas por tipo de archivo para cosas con dominio: no existen `forms/`, `contexts/`, `constants/`, `helper/`, `normalize/`, `styles/*.js` (§3.1.4).
   - Los tests van junto al archivo que prueban.
2. **Datos de servidor: solo TanStack Query.** Prohibido `useEffect` + `fetch`, y prohibido guardar respuestas de la API en Context o Zustand.
3. **Query keys** desde la fábrica de `api/queryKeys.ts`, nunca strings sueltos.
4. **HTTP** siempre a través de `api/client.ts` — él inyecta el JWT y traduce el `ProblemDetail`.
5. **UI: shadcn/ui + Tailwind.** Nada de MUI, Emotion ni styled-components. Si el MCP `shadcn-ui` está disponible, consultar el componente real antes de usarlo, para no inventar props.
6. **Formularios**: react-hook-form + zod, con el esquema en `schemas.ts` de la feature. El tipo del formulario sale de `z.infer` y queda atado al payload del dominio con `Expect<Equals<…>>` (§3.2, regla 7).
   El formulario es **puro**: recibe `defaultValues` y `onSubmit`, y no conoce la mutación, ni cierra modales, ni invalida queries. Para mostrarlo en un modal se crea un `<Entidad><Acción>Dialog` aparte que compone `FormDialog` (de `components/`) con el formulario y **es el dueño de la mutación** (§3.3, #110).
7. **Tipos**: un tipo base por entidad en `features/<dominio>/types.ts`, espejo del `...Response` del backend. Las variantes se **derivan** con `Pick` / `Omit` (usar `StrictOmit`) / `Partial` / `Record`, nunca se re-declaran a mano. Enums del backend = unión de literales, no `enum` de TS. Nunca `any`; para lo desconocido, `unknown`. Detalle en `docs/arquitectura.md` §3.2.
8. **Estado de UI**: local por defecto. Librería si ya existe resuelto (`sonner`, `next-themes`), Context si pertenece a un subárbol o monta UI, Zustand solo si es global y plano —contexto de rol activo, preferencias— (#105). Ninguno de los tres guarda datos de servidor.
9. **Naming**: componentes y páginas `PascalCase.tsx` (las páginas terminan en `Page`), hooks `useCamelCase.ts`, el resto `camelCase.ts`.
10. **Fechas**: se formatean con `lib/date.ts` (`Intl` nativo), pasando la zona del perfil del usuario. No se instala librería de fechas ni se incrusta un locale (#111).
11. **Tailwind 4**: los tokens del tema van en el bloque `@theme` de `src/styles/globals.css`. No existe `tailwind.config.ts` — si una receta lo menciona, es de la v3. **Los tokens los define el design system** (Claude Design, #130) y se transcriben al `@theme`: si un valor no está en el tema, se agrega primero al diseño (#118).
12. **Textos: ningún string visible se escribe en el JSX.** Todo pasa por `t('espacio.clave')` de i18next, con el JSON en `src/locales/es/<feature>.json` (#117).
13. **`staleTime` explícito en cada query**, tomado de la política de `config/query.ts` (§3.3). Nunca se deja el default de `0`.

## Pasos

1. Si el componente reconstruye algo que existía, revisar el equivalente en `legacy/frontend-next/` para replicar el comportamiento real en vez de inventarlo. `legacy/` es de solo lectura.
2. Si la pieza ya está en el design system, leerla con `DesignSync` (`get_file`) en vez de aproximarla visualmente.
3. Crear o reusar el hook de TanStack Query en `features/<dominio>/api/`.
4. Construir la UI sobre las primitivas de `components/ui` antes de crear una nueva.
5. Si es página: va en `src/routes/`, exporta `Component` además de su nombre, y se registra en `src/routes/router.tsx` con `lazy` bajo el layout que corresponda. Su path sale de `config/paths.ts` (§3.1.6).
6. Test con Vitest + React Testing Library si tiene lógica o comportamiento condicional.
7. Si es un flujo crítico (login, crear mesa, postularse, subir archivo), agregarlo al spec de Playwright en `e2e/`.
