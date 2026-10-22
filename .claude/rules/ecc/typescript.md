---
paths:
  - "**/*.ts"
  - "**/*.tsx"
---
# ECC — TypeScript y React (curado para CentralDungeon)

> Complementa la skill `arquitectura` §3, §5.2 y §6.2, y la skill `diseno`; **ante un choque, mandan ellas**. No se adoptan de ECC: la interfaz `ApiResponse` con `success/data/error` (la API devuelve el DTO desnudo, ADR-04 §3), el «repository pattern» del frontend (HTTP solo por `api/client.ts` y TanStack Query, §3.3) ni el `try/catch` alrededor de queries (los errores los maneja TanStack Query).

## Tipos

- Todo `export` (función, hook, utilidad) lleva **tipos explícitos de parámetros y retorno**. Lo local y obvio se deja inferir.
- `interface` para el espejo de cada `…Response` y `type` para los derivados y las uniones (§3.2). Se prefieren uniones de literales a `enum`.
- **`unknown` en vez de `any`** para lo externo o lo que no es de confianza, con narrowing antes de usarlo (`instanceof`, zod). Nunca `any`, como dice la regla dura 6.
- Las props se tipan con una `interface` con nombre, campo por campo, incluidos los callbacks. Nada de `React.FC`.

## Inmutabilidad (aquí sí es absoluta)

- El estado de React, de Zustand y la caché de TanStack Query **nunca se mutan**: se reemplazan con spread, `map` o `filter`. Mutar rompe el re-render y la caché.
- Los parámetros que no se deben tocar se reciben como `Readonly<T>`.

## Validación

- Lo que entra al sistema (formularios, query params de la URL) pasa por un esquema **zod**, y el tipo se infiere del esquema con `z.infer` en vez de declararse por separado.

## Higiene

- Nada de `console.log` en código que se commitea. El hook de ECC avisa si queda alguno.
- `useEffect` solo para sincronizar con algo externo (timers, suscripciones, DOM), nunca para traer datos (regla dura 11).
