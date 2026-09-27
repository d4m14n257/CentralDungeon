---
name: arquitectura-frontend
description: CentralDungeon's frontend architecture rules — where each file goes, features versus screens, routing, the type model, server and UI state, i18n, forms, dates and styles. Use before writing, changing or reviewing any code in frontend/, and whenever code or decisiones.md cites "arquitectura-frontend §3.x".
---

# Arquitectura del frontend

Las reglas de cómo se escribe el frontend de CentralDungeon. **Son la fuente**: el código y `docs/decisiones.md` citan estas secciones por su número (`arquitectura-frontend §3.1.2`), que es el mismo que tenían cuando vivían en `docs/arquitectura.md`. El stack y sus versiones siguen en `docs/arquitectura.md` §1; qué componentes existen ya, en `docs/frontend-diseno.md` §5, que se lee antes de crear uno.

## Reglas fijas

1. **Feature-first, pero las pantallas van aparte.** Un componente de dominio vive en `src/features/<dominio>/`, en `components/`, `api/` o `hooks/`. **Las páginas van en `src/routes/`, nunca dentro de una feature.** Si el nombre o las props mencionan un concepto del dominio, va en la feature aunque parezca reutilizable; si no menciona ninguno, sube a la capa transversal de la raíz (`components/`, `hooks/`, `lib/`, `types/`) recién **cuando una segunda feature ya lo necesita**. → §3.1.1, §3.1.2
2. **Una feature nunca importa de otra**, sin excepciones. Si una pantalla necesita varios dominios, cada bloque es un `…Section` que vive en su feature, hace su propia query y recibe un **id**, no la entidad. → §3.1.5
3. **Superficie pública**: cada feature exporta lo suyo en `features/<dominio>/index.ts`; desde afuera se importa `@/features/tables`, nunca una ruta interna. Es el único barrel (#114). `components/` de la feature es **plano**, con sufijo (`Form`, `Dialog`, `Section`, `Card`, `Badge`, `List`, `Editor`). No existen `forms/`, `contexts/`, `constants/`, `helper/`, `modals/`. → §3.1.3, §3.1.4
4. **Ruteo**: la página exporta `Component`, se registra en `src/routes/router.tsx` con `lazy` bajo su layout, y **todo enlace a ella sale de un builder de `config/paths.ts`**, nunca de un string escrito a mano. Los contextos no son autorización: no hay guardias de rol (#103). → §3.1.6
5. **Tipos**: un tipo base por entidad en `features/<dominio>/types.ts`, espejo del `…Response` del backend. Las variantes se **derivan** con `Pick` / `StrictOmit` / `Partial` / `Record`, nunca se re-declaran. Enums del backend = unión de literales, no `enum` de TS. Nunca `any`; para lo desconocido, `unknown`. → §3.2
6. **Datos de servidor: solo TanStack Query.** Prohibido `useEffect` + `fetch`, y prohibido guardar respuestas de la API en Context o Zustand. **Query keys** desde la fábrica de `api/queryKeys.ts`, y **`staleTime` explícito** tomado de `config/query.ts`. → §3.3
7. **HTTP** siempre a través de `api/client.ts`, que inyecta el JWT, reintenta una vez ante `401` y traduce el `ProblemDetail`. → §3.3
8. **Estado de UI**: local por defecto. Librería si ya existe resuelto (`sonner`, `next-themes`), Context si pertenece a un subárbol o monta UI, Zustand solo si es global y plano (#105). → §3.3
9. **Formularios**: react-hook-form + zod, con el esquema en `schemas.ts` de la feature y el tipo atado al payload con `Expect<Equals<…>>`. El formulario es **puro**; el `<Entidad><Acción>Dialog` que lo muestra es el dueño de la mutación (#110). → §3.2 regla 7, §3.3
10. **Textos**: ningún string visible en el JSX. Todo pasa por `t('espacio.clave')`, y **toda clave nueva se agrega en `es` y en `en` en el mismo commit** (#117, #198). El backend manda códigos y parámetros, nunca frases (#197). → §3.3
11. **Estilos**: shadcn/ui + Tailwind 4, con los tokens en el `@theme` de `src/styles/globals.css`, transcripto desde `design/build.py`. No existe `tailwind.config.ts`. Ningún valor de estilo suelto (#118, #130). → §3.3
12. **Fechas** con `lib/date.ts` (`Intl` nativo) y la zona del perfil. Sin librería de fechas (#111). → §3.3
13. **Naming**: componentes y páginas `PascalCase.tsx` (las páginas terminan en `Page`), hooks `useCamelCase.ts`, el resto `camelCase.ts`. Tests junto al archivo. → §3.1.3, §3.4
14. **Todo `export` lleva JSDoc en inglés**, y los `props` se documentan campo por campo en su `interface`. `components/ui/` es código generado por shadcn y no se toca.

## El detalle, por sección

| § | Qué cubre | Archivo |
|---|---|---|
| 3.1 – 3.1.6 | El patrón, dónde va cada archivo, cuándo algo sube a la raíz, nombres y superficie pública, las carpetas que no existen, feature ≠ pantalla, ruteo | [`references/estructura.md`](references/estructura.md) |
| 3.2 | Modelo de tipos: un tipo base por entidad, el resto derivado (diez reglas con ejemplos) | [`references/tipos.md`](references/tipos.md) |
| 3.3 | Reglas de datos, paginación, query keys, cliente, estado de UI, tiempo real, caché, sesión, i18n, formularios, fechas y estilos | [`references/reglas.md`](references/reglas.md) |
| 3.4 | Testing: Vitest + Testing Library, Playwright | [`references/testing.md`](references/testing.md) |

Para agregar un componente o una página, el procedimiento está en la skill `nuevo-componente-react`.
