---
adr: 241
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0241: /my/files es de players y masters: quien no tiene el rol no lo ve

## Decisión

El ítem «Mis archivos» del menú de cuenta está **ausente** para quien no es `Player` ni `Master`, y la pantalla, si se entra por URL, dice por qué en vez de mostrar una biblioteca vacía con un botón de subir que no puede funcionar. La regla vive en `useHasPersonalLibrary`: `Player` o `Master` o `hasManagedTables`, exactamente la forma que ya usaba `useAvailableContexts` para los contextos del switcher. Se corrigió además el javadoc de `FileController.listMyCategories` —y su espejo en `useMyCategories.ts`— que afirmaba «Never empty - every account is a `Player` (#38)».

## Por qué

Se vio probando #240 con la cuenta `admin-1`: llegaba a `/my/files`, y ahí no había nada que buscar ni nada que subir — hasta el comando `/file_categories` se ofrecía con la lista vacía. Llenar una biblioteca propia es lo que hacen los flujos de jugador y de master —una postulación, una entrega, el material de una mesa—; una cuenta que no pasa por ninguno no tiene biblioteca, y lo que la plataforma publica vive en `/admin/files`, que es otra pantalla con otro trabajo (#237). **Se decide por rol y no por un dato derivado del usuario**, que es como se decide todo lo que la shell muestra o esconde: `me.roles` es para decidir qué mostrar, nunca qué permitir (#103). Un primer intento preguntó en cambio si el servidor le daba algún cajón; da el mismo resultado —`personalCategoriesOf` se calcula con esos mismos tres datos— pero es la regla escrita dos veces, en dos formas distintas, y encima obligaba al header a pedir una query de archivos para pintar un ítem de menú. Leyendo el perfil que el header ya carga para el chip de contexto, el menú no cuesta ningún request. **Los roles son acumulables**, así que lo que se pregunta es si tiene el rol, nunca si tiene *solo* ese: un admin que además juega ve su biblioteca. Y `hasManagedTables` es la misma excepción que hace el contexto Master (#135): a quien un admin asignó a una sola mesa nadie le dio el rol, y el material de esa mesa igual es suyo. **No es un guard de ruta**: el contexto es organización de la navegación y no seguridad (#103), los layouts no chequean roles, el backend autoriza endpoint por endpoint y la pantalla pinta el estado. Y `#38` quedó aclarado: dice que todo usuario **nuevo** se crea con `Player`, que es una afirmación sobre el alta y no un invariante — un rol se puede revocar, así que hay cuentas con solo `Admin`, solo `Master` o solo `Player`. Pendiente, no resuelto acá: `/my/schedule`, al lado en el mismo menú, tiene la misma pregunta abierta y no se tocó. Verificado con `npx vitest run` (242) y a mano contra el backend real: con `admin-1` no aparece el ítem y la pantalla explica; con `master-1` aparece y la biblioteca funciona entera.
