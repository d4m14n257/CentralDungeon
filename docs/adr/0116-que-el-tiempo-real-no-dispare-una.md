---
adr: 116
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0116: Que el tiempo real no dispare una tormenta de peticiones

## Decisión

**Tres reglas**: el mensaje trae tipo e id y se invalida solo esa rama de `queryKeys`; se confía en que `invalidateQueries` refetchea únicamente las queries **activas**; y **al reconectar se invalida todo lo activo**. Se agrega un tercer destino, `/topic/tables`. Y **`staleTime` explícito en toda query**, según la tabla de skill `arquitectura` §3.3

## Por qué

Completa #101, que fijó lo difícil —el mensaje es señal, no contenido— pero no dijo qué pasa cuando hay cincuenta personas conectadas. Sin la regla 1, cada publicación invalidaría media caché de cada cliente; con ella, una mesa nueva mueve una sola lista. La regla 3 tapa el agujero real del transporte: mientras el socket estuvo caído se perdieron mensajes y la caché quedó mintiendo sin que nadie lo sepa. El `staleTime` explícito es la otra mitad: el default de TanStack Query es `0`, así que sin decidirlo cada montaje de pantalla vuelve a pedir todo. Faltaba `/topic/tables` porque #101 solo cubría lo personal y la bandeja de admins; una mesa recién publicada no le llegaba a nadie navegando el explorador.
