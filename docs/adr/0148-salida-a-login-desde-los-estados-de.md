---
adr: 148
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0148: Salida a /login desde los estados de error del callback

## Decisión

Los tres estados de error de `OAuthCallbackPage` (no-es-miembro, bloqueado, error genérico) no tenían forma de volver a `/login` — solo acciones que reintentan el mismo camino roto (unirse, reintentar) o, en el caso de bloqueado, ninguna acción. Se agregó un botón "Volver al login" (`Link` de React Router, no `<a>` — no hace falta recargar) a los tres: `ghost` en no-es-miembro (tercera acción, la de menor peso, detrás de unirse/reintentar), `outline` como única acción en bloqueado y en el error genérico. Reflejado también en `design/build.py` (`sc_callback`), y de paso se corrigió el texto del segundo botón del estado "no-es-miembro" en el mock, que decía "Ahora no" y hacía años que no coincidía con lo que el código realmente ofrece ("Ya me uní, reintentar")

## Por qué

Lo pidió el usuario probando el bloqueo real con su cuenta (#147): llegaba a la pantalla y se quedaba sin salida. Bloqueado no tiene ninguna acción que tenga sentido —reintentar no cambia el `status`— así que "volver al login" pasó de ser la tercera opción a ser la única. Verificado con navegación directa a las dos URLs de error y click real en el botón confirmando que cae en `/login` en los dos casos; la suite e2e no corrió en esta verificación porque el backend estaba deliberadamente en perfil `dev` (sin `test`) para probar el bloqueo contra Discord real del punto anterior — no por una regresión de este cambio.
