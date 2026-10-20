---
adr: 260
estado: Aceptada
fecha: 2026-09-18
---

# ADR-0260: La bandeja se mantiene viva con refetchInterval, y el WebSocket la reemplaza en F6

## Decisión

15 segundos más `refetchOnWindowFocus`, con el valor en `config/query.ts` junto a los `staleTime`, no suelto en el hook.

## Por qué

El criterio de aceptación de F3.3 es que a un admin le desaparezca **sin recargar la página a mano** el ítem que otro reservó, y el WebSocket es F6 (#101). El frontend **solo** refrescaba por invalidación tras mutación —el único `refetchInterval` del proyecto vivía en `useBackendStatus`—, así que el criterio no se cumplía con lo que había. Vive en `config/query.ts` porque es una decisión de comportamiento de la plataforma, como `pageSize` y los `staleTime`: en el hook sería una constante que nadie encuentra el día que haya que cambiarla. **Y se agravaba con lo que la misma rebanada agregó**: la consulta de pedidos sin `join fetch` pintaba 25 filas con hasta 200 `SELECT` extra, lo que con un refetch cada 15 segundos deja de ser un costo por visita y pasa a ser tráfico constante por cada admin conectado. El `refetch` y el N+1 son inofensivos por separado.
