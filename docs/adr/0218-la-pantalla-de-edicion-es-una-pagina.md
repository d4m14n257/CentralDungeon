---
adr: 218
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0218: La pantalla de edición es una página con secciones, no el wizard

## Decisión

**`/master/tables/:id/edit` es una ruta hermana del detalle** —como `/master/tables/new` lo es de la lista— y presenta los mismos campos del wizard **en una sola página con cuatro secciones**, no en cuatro pasos

## Por qué

Los pasos del wizard existen para no pedirle quince valores de golpe a alguien que no decidió ninguno; quien corrige una mesa ya los tiene todos y necesita ver de una qué cambia — obligarlo a pasar por cuatro pantallas para tocar un campo es trabajo puro. Es la misma decisión de forma que #189 tomó del lado del contrato: el `PUT` reemplaza la mesa entera, así que la pantalla que lo alimenta tiene que mostrarla entera. Hermana y no pestaña porque las siete pestañas del diseño son vistas de la mesa y esto es un formulario: meterlo adentro del marco de pestañas le pone encima una navegación que no sirve mientras se edita. **Cierra el hueco que F1.2 dejó anotado**: el `PUT`, su hook y sus tipos existían desde entonces sin pantalla, así que una mesa en `ChangesRequested` no se podía corregir — el botón de reenviar a revisión estaba, la forma de aplicar la corrección no. El botón «Editar» aparece solo en `Preparation` y `ChangesRequested`, que es donde el backend acepta el `PUT`: un botón que aparece cuando no puede funcionar es peor que no tenerlo (principio 2 de `frontend-diseno.md` §1).
