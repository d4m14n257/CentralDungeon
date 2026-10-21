---
adr: 292
estado: Aceptada
fecha: 2026-10-20
modifica: [290]
---

# ADR-0292: Los documentos de referencia se disuelven en las skills, con su numeración

> Precisa #290: la rule `stack.md` se carga ahora al tocar `references/stack.md`, no `docs/arquitectura.md`

## Decisión

Cuatro documentos de `docs/` se borran y su contenido pasa a la skill que ya era dueña del tema, **conservando el número de sección**, como ya se había hecho en F4.0 y en #273 y #274:

| Documento | Ahora | Numeración |
|---|---|---|
| `docs/arquitectura.md` §1 (stack y versiones) y §5 (deuda del proyecto viejo) | `arquitectura`, `references/stack.md` | §1 sigue siendo §1; §5 pasa a §1.4 |
| `docs/arquitectura.md` §6 (`.env` y despliegue) | `arquitectura`, `references/despliegue.md` | §6.1 y §6.2 pasan a §8.1 y §8.2 |
| `docs/modelo-datos.md` §2 (cambios del schema heredado) y §3 (el ER) | `arquitectura`, `references/modelo-datos/historia-y-er.md` | §4.2 y §4.3, con el prefijo `4.` que ya regía |
| `docs/frontend-diseno.md` §1 (principios), §2 (navegación y sitemap), §4 (pantallas clave) y §6 (rescate del legacy) | `diseno`, `references/navegacion.md` y `references/pantallas.md` | los mismos números: §3 y §5 ya eran de `diseno` |
| `docs/mcp-y-skills.md` (MCP, variables, Claude Design, `archify`) | `entorno-local`, `references/herramientas.md` | §6 a §8 |

Lo que `mcp-y-skills.md` decía de las skills, las rules y los agentes del repo no se movió: ya está en `CLAUDE.md`, y tenerlo en dos lados era la redundancia que esto elimina.

**Los wireframes en ASCII de `frontend-diseno.md` §4 no se trajeron**: habían quedado viejos —rutas anteriores a #222, los filtros que reemplazó el buscador (#242), la pestaña Estado que quitó #287— y la pantalla dibujada de verdad es la preview de `design/out/screen-*.html`. Las reglas que vivían debajo de cada wireframe sí se trajeron, con las rutas actuales.

Las citas del código, las skills, las rules, los agentes y las ADRs a esos documentos se reescribieron a su sección nueva (`frontend-diseno.md` §1 → `diseno` §1, `arquitectura.md` §6.1 → `arquitectura` §8.1). Las dos que viven en migraciones aplicadas (`V2__seed.sql`, `V12__registration_status_changes.sql`) no se tocan (regla dura 9), y siguen resolviendo por el prefijo `4.`: «`modelo-datos.md` §1» es `arquitectura` §4.1, y «#6» es §4.6.

`docs/` queda con lo que no es una regla de escritura: las ADRs, el plan de fases, la fase en curso y los diagramas.

## Por qué

Los cuatro ya eran, cada uno, un cascarón: abrían diciendo «esto se mudó a la skill» y conservaban la mitad que nadie había movido. Esa mitad era normativa —los principios de diseño se citaban 32 veces desde el código, y el ER es lo que la regla dura 10 obliga a actualizar— pero vivía donde Claude no la carga al trabajar, así que la regla se aplicaba solo si alguien se acordaba de abrir el documento. En la skill se carga al tocar el tema, y cada cosa queda dicha una sola vez.

Conservar el número era otra vez la condición: así una cita vieja —incluidas las dos de migraciones que no se pueden editar— sigue encontrando su sección sin un mapa aparte.
