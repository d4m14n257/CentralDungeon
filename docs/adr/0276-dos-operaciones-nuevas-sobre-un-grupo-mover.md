---
adr: 276
estado: Aceptada
fecha: 2026-09-29
modifica: [55, 59, 183]
---

# ADR-0276: Dos operaciones nuevas sobre un grupo: mover un equivalente (reassign) y hacer principal (promote)

> Precisa #55, #59 y #183

## Decisión

`POST /admin/catalogs/{type}/{id}/reassign {canonicalId}` mueve **un alias** a otro grupo en una transacción: un canónico no se mueve solo —arrastra a su grupo, y eso es `merge`—, y el destino tiene que ser un canónico `Accepted` distinto del grupo actual. `POST /admin/catalogs/{type}/{id}/promote` hace de un alias `Accepted` **el principal de su propio grupo**: el alias queda canónico y el canónico anterior y los demás alias pasan a apuntarle; nadie sale del grupo, nada sale de circulación y ninguna mesa se toca (#56). Las dos con `@PreAuthorize("hasAnyRole('ADMIN','OWNER')")` en el método y su IT de autorización (`AdminCatalogApiIT`). Los dos gestos tienen además su diálogo o su entrada de menú para quien no arrastra (`ReassignCatalogValueDialog`, «Hacer principal»). Las ocho operaciones siguen escritas una vez en `AbstractCatalogService`

## Por qué

Son los dos gestos que el lienzo de #275 hace naturales y que el modelo no tenía. **Mover** era un `split` seguido de un `merge`: dos requests, y un fallo entre las dos dejaba al alias suelto como grupo propio. **Cambiar el principal** solo existía como efecto de dar de baja al que estaba (#183), que además lo sacaba de circulación, y eso no siempre es lo que se quiere. La profundidad 1 de #59 no cambia: las dos operaciones la sostienen en cada paso
