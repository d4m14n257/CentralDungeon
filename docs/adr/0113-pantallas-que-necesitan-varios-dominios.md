---
adr: 113
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0113: Pantallas que necesitan varios dominios

## Decisión

**Una feature es un dominio, no una pantalla.** La única excepción a "una feature no importa de otra" es `routes/`: una página compone componentes de otras features. Cada bloque es un componente `…Section` que vive en su feature, lanza su propia query y monta sus propios diálogos, y **recibe un identificador, no una entidad**

## Por qué

La pantalla de detalle de mesa necesita seis dominios a la vez. En el legacy eso era `PreparationStatus`: ~300 líneas importando de todos lados, con seis `useModal` en paralelo, que además era un *componente* haciendo de página. Sin esta excepción la regla de #112 no tendría salida legítima para esa pantalla. Se acota a `routes/` porque las páginas son hojas —nada las importa salvo el router—, así que no pueden generar ciclos entre features; `components/`, `api/`, `hooks/` y `types.ts` siguen sin poder cruzarse. Que el contrato sea el `id` y no la entidad es lo que evita que `features/files` termine dependiendo del tipo `GameTable`: toda la dependencia entre dos features cabe en una prop.
