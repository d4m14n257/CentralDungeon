# 3.2 Modelo de tipos: un tipo base por entidad, el resto derivado

> Parte de la skill `arquitectura-frontend`. Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


Esta sección es la contraparte frontend de §2.3 y la razón principal por la que se conserva TypeScript al migrar de Next.js a React puro.

La regla es: **por cada entidad del dominio se escribe a mano un único tipo base, y todas las variantes se derivan de él con utility types.** El problema que resuelve es concreto: si `GameTable`, `CreateGameTableInput`, `UpdateGameTableInput` y `GameTableCardProps` se declaran cada una por separado, son cuatro copias del mismo modelo que se desincronizan en silencio — el día que el backend renombra un campo, tres de las cuatro siguen compilando y el bug aparece en runtime.

**El tipo base** vive en `features/<dominio>/types.ts` y es el espejo exacto del `...Response` del backend:

```ts
// features/tables/types.ts

/** Espejo de GameTableResponse. Único tipo de esta feature escrito a mano. */
export interface GameTable {
  id: string;
  name: string;
  description: string;
  tableTypeId: string;
  status: GameTableStatus;
  ownerId: string;
  createdAt: string;   // ISO-8601 UTC — la conversión a zona local es del frontend (§2.5)
  updatedAt: string;
}
```

**Todo lo demás se deriva**, en el mismo archivo, debajo del base:

```ts
// Payload de creación: sin lo que genera el servidor.
export type CreateGameTableInput = StrictOmit<GameTable, 'id' | 'ownerId' | 'status' | 'createdAt' | 'updatedAt'>;

// PATCH: los mismos campos, todos opcionales.
export type UpdateGameTableInput = Partial<CreateGameTableInput>;

// Lo que necesita una card de listado, y nada más.
export type GameTableSummary = Pick<GameTable, 'id' | 'name' | 'status' | 'tableTypeId'>;

// El detalle: la base más las relaciones que solo trae ese endpoint.
export type GameTableDetail = GameTable & {
  schedules: TableSchedule[];
  masters: MasterSummary[];
  tags: Tag[];
};

// Formulario a medio llenar: todo opcional menos la identidad.
export type GameTableDraft = Partial<GameTable> & Pick<GameTable, 'id'>;
```

**Qué utility type usar para qué** ([referencia completa](https://www.typescriptlang.org/docs/handbook/utility-types.html)):

| Utility | Cuándo |
|---|---|
| `Pick<T, K>` | vistas reducidas: la card de un listado, las opciones de un selector |
| `Omit<T, K>` | payloads de escritura: sacar lo que genera el servidor (`id`, `createdAt`, `updatedAt`) |
| `Partial<T>` | payloads de PATCH y estado de formularios a medio llenar |
| `Required<T>` | pasar de un draft a un valor ya validado |
| `Readonly<T>` | datos de servidor que un componente recibe y no debe mutar |
| `Record<K, V>` | diccionarios por clave cerrada: `Record<GameTableStatus, string>` para labels — obliga a cubrir todos los estados |
| `Exclude<T, U>` / `Extract<T, U>` | acotar uniones de estado: `Exclude<GameTableStatus, 'Archived'>` |
| `NonNullable<T>` | estrechar un campo opcional después de comprobarlo |
| `ReturnType<T>` / `Awaited<T>` | derivar el tipo de una respuesta desde la función del cliente HTTP en vez de re-declararlo |
| `Parameters<T>` | reusar la firma de una función en un wrapper |
| `Capitalize` / `Uppercase` y familia | claves derivadas en tipos de plantilla, casos puntuales |

**Reglas**

1. **Un tipo base por entidad**, en `features/<dominio>/types.ts`. Si el backend agrega un campo, se agrega ahí y todos los derivados se actualizan solos.
2. **Prohibido re-declarar a mano un tipo que sea subconjunto o variante de otro.** Si es "lo mismo pero sin X", es `Omit`. Si es "lo mismo pero opcional", es `Partial`. Si es "solo estos tres campos", es `Pick`.
3. **Los derivados se declaran junto al base**, no dispersos por los componentes. Un componente importa el tipo que necesita; no lo inventa en sus props.
4. **`interface` para el base** (extensible, mejores mensajes de error), **`type` para los derivados** (los utility types devuelven types).
5. **Nunca `any`.** Para lo genuinamente desconocido, `unknown`, y se estrecha antes de usarlo.
6. **`Omit` no valida sus claves**: `Omit<GameTable, 'createdAtt'>` compila y no quita nada. Por eso los payloads de escritura usan el helper propio, que sí las verifica:

```ts
// types/utils.ts
export type StrictOmit<T, K extends keyof T> = Omit<T, K>;

// Aserciones de tipos en tiempo de compilación.
export type Expect<T extends true> = T;
export type Equals<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
```

7. **El esquema zod y el payload del dominio no pueden divergir.** El tipo del formulario sale del esquema con `z.infer`, y una aserción de tipo lo ata al derivado del dominio: si dejan de coincidir, falla la compilación, no el submit.

```ts
// features/tables/schemas.ts
export const createGameTableSchema = z.object({ /* ... */ });
export type CreateGameTableForm = z.infer<typeof createGameTableSchema>;

type _CheckCreatePayload = Expect<Equals<CreateGameTableForm, CreateGameTableInput>>;
```

8. **Los tipos transversales de la API viven en `types/api.ts`** y son genéricos: se instancian (`PageResponse<GameTableSummary>`), no se re-declaran por feature.
9. **Los enums son uniones de literales, no `enum` de TypeScript.** El backend los serializa como string (§2.3):

```ts
export type GameTableStatus = 'Open' | 'InProgress' | 'Closed' | 'Finished';
```

Un `enum` de TS genera código en runtime, no es tree-shakeable y no coincide estructuralmente con el string que llega por la red. Con la unión, además, `Record<GameTableStatus, string>` obliga a cubrir todos los casos al mapear a labels o a variantes de badge.

10. **`tsconfig.json`**: `strict: true` más `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes` y `verbatimModuleSyntax`. Sin `strict` toda esta sección es decorativa.

> El tipo base se escribe a mano y se mantiene sincronizado con el `...Response` de Java por disciplina. Generarlo desde el OpenAPI que ya publica springdoc (§1.1) eliminaría ese trabajo manual; está anotado como candidato en `decisiones.md`, pero **hoy no está adoptado** y las reglas de arriba asumen tipos escritos a mano.
