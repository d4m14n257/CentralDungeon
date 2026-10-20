---
adr: 150
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0150: isLoadingError, no isError, en las ocho pantallas de lectura

## Decisión

Las ocho pantallas que leen listas o una entidad (`TableListPage`, `NotificationsPage`, `MyTablesPage`, `MasterTablesPage`, `MyApplicationsPage`, `TableDetailPage`, `MasterTableDetailPage`, `MasterTableCandidatesTab`) usaban `isError` de TanStack Query para decidir mostrar `ErrorState`. Se cambiaron las ocho a `isLoadingError` (`isError && !hasData`, ya expuesto por la librería junto a `isRefetchError` — no hubo que agregar nada). En las cinco de lista, la condición del bloque de error pasó de `isError` a `isLoadingError`, dejando `data && ...` intacto. En las tres de detalle (que retornan `<ErrorState/>` temprano y reemplazan la pantalla entera), el `if (isError || !dato)` pasó a `if (isLoadingError || !dato)`

## Por qué

Lo encontró el usuario probando el explorador con la conexión cortada: veía la lista de mesas ya cargada **y** el bloque "Algo salió mal" al mismo tiempo, en vez de uno u otro. Confirmado leyendo el reducer real de `@tanstack/query-core` (no era una sospecha): la acción `"error"` pone `status: "error"` sin condición ni chequeo de si hay `data` cacheada, y no la borra — así que un refetch de fondo que falla dispara `isError = true` con `data` todavía poblado, lo que hace que `isError && <ErrorState/>` y `data && <Lista/>` sean ciertos los dos a la vez. La librería ya distingue exactamente este caso con dos flags que nadie usaba: `isLoadingError` (falló y no hay nada que mostrar — ahí sí correspondía) e `isRefetchError` (falló pero hay datos servibles — no amerita reemplazar la pantalla, alcanza con el indicador global de conexión). Las tres pantallas de detalle tenían el problema inverso y más grave: con `isError` puro, un refetch de fondo fallido **reemplazaba contenido bueno que ya estaba en pantalla** por un error — parpadeo de contenido que sí andaba. Verificado en vivo con un caso real, no simulado: se cargó el explorador, se esperó a que el `staleTime` de 30s venciera, se mató el backend, y se forzó un remount por navegación SPA (nunca `location.reload()`/recarga completa, que habría vaciado la caché en memoria de TanStack Query y invalidado la prueba). Resultado: la lista se mantuvo completa, sin el bloque de error superpuesto, con el indicador global en rojo como única señal.
