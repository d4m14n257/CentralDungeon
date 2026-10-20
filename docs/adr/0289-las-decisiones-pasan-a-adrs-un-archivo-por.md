---
adr: 289
estado: Aceptada
fecha: 2026-10-20
---

# ADR-0289: Las decisiones pasan a ADRs, un archivo por decisión, con el número conservado

> Reemplaza la forma de `docs/decisiones.md`, no su contenido

## Decisión

`docs/decisiones.md` se parte en `docs/adr/`: cada fila de su tabla es un archivo `NNNN-slug.md` con frontmatter (`adr`, `estado`, `fecha`, y `modifica` / `modificada-por` / `reemplazada-por` cuando corresponde) y dos secciones, **Decisión** (la antigua columna *Resolución*) y **Por qué**. **La ADR-n es la antigua #n**: no se renumera nada. La fecha de cada una es la del commit que agregó su fila.

Lo que no era una decisión se movió a `docs/adr/README.md` —el contexto de la reescritura, lo que se rescata de `CentralDungeonBackend`, lo **abierto** y el índice M1–M32—, y el razonamiento del karma, a la ADR-0096, que es la que lo cerró. El README es además el índice y dice cómo se escribe una nueva.

Estados: **Reemplazada** solo cuando la nueva la deja entera sin efecto (#162 por #266; #167 y #168 por #231). Las que otra corrige o precisa en parte siguen **Aceptadas** con `modificada-por`, porque lo que no se corrigió sigue rigiendo.

`ecc:architecture-decision-records` sigue sin usarse tal cual: trae su propia plantilla en inglés. El formato es el de `docs/adr/README.md`, y lo aplica el agente `escriba-adr`.

## Por qué

Una tabla de 288 filas en un archivo de 466 líneas dejó de ser consultable: cada fila arrastraba tres párrafos en una celda, no había forma de marcar que una decisión había sido corregida salvo tachando texto adentro, y cualquier lectura para buscar una cargaba las 288. Un archivo por decisión se abre solo, se enlaza, lleva su estado en el frontmatter y entra en git con su propia historia.

**Conservar el número era la condición**: el código cita `#n` unas cinco mil veces en casi ochocientos archivos, y las skills y los docs, otras dos mil. Renumerar habría sido reescribir todas esas citas —incluidas las de migraciones Flyway aplicadas, que no se pueden tocar (regla dura 9)— a cambio de nada.
