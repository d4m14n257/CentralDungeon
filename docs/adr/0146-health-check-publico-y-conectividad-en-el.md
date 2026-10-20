---
adr: 146
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0146: Health check público y conectividad en el frontend

## Decisión

**`GET /api/v1/health`**, feature propia `com.centraldungeon.health` (controller + service + `HealthResponse` — sin repository, no hay nada que persistir), público en `SecurityConfig` porque `/login` lo consulta sin sesión. Devuelve `200 {"status":"UP"}` siempre que el proceso conteste; **no** mide dependencias (DB, etc.) — la pregunta que resuelve es "¿hay un backend ahí?", no "¿está todo sano?". Del lado del frontend: `useBackendStatus()` (TanStack Query, sondea cada 15s, `retry: false` para no demorar el aviso), un `BackendStatusIndicator` fijo montado en `RootLayout` — visible en cualquier pantalla, incluida `/login`, que no tiene header —, el botón de "Entrar con Discord" deshabilitado con motivo explícito cuando está offline (principio 2), y un `MutationCache.onError` global en `queryClient` que traduce cualquier mutación sin manejo propio a un toast en vez de fallar en silencio

## Por qué

El pedido salió de un caso real de esta sesión: el usuario probó el login real sin saber que su backend corría con el perfil `test` (que redirige el OAuth2 a un Discord falso), y no había ninguna señal en la interfaz de que el problema fuera de conectividad y no de su cuenta. Auditando las mutaciones existentes (`useApplyToTable`, `useAcceptRegistration`, etc.) confirmé que **ninguna tenía `onError`**: si fallan hoy, el botón deja de estar "pending" y no pasa nada más — silencioso, no roto, pero indistinguible de "no hizo nada". El resto de la resiliencia ya estaba bien encaminado y no hizo falta tocarlo: las queries de lectura ya siguen el patrón de los cuatro estados obligatorios (`isPending`/`isError`/`data &&`) en todas las pantallas, y `AuthProvider` ya atrapa el fallo de red del `refreshSession()` inicial con un `.catch()`, así que la app arrancaba bien en `/login` con el backend caído desde antes de este cambio. El indicador reusa los tokens de estado ya medidos para AA (`state-open` verde / `state-canceled` rojo) en vez de inventar un color nuevo. Se decidió que el indicador se vea siempre, en los dos estados — no solo al fallar —, porque así lo pidió explícitamente quien lo iba a usar para depurar la conexión en el momento.
