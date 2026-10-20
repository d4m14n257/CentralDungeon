---
adr: 253
estado: Aceptada
fecha: 2026-09-14
---

# ADR-0253: PlatformRole cruza HTTP con @JsonValue, y un cuerpo malformado es 400 y no 500

## Decisión

`PlatformRole.roleName()` lleva `@JsonValue`: el mismo método define la ortografía de ida y de vuelta, así que no pueden divergir. Y `GlobalExceptionHandler.handleUnreadableBody` mapea `HttpMessageNotReadableException` a `400 VALIDATION_ERROR` con `detail` genérico.

## Por qué

Es el único enum del proyecto cuyas constantes son `PLAYER/MASTER/ADMIN/OWNER` mientras la API publica `"Player"`; todos los demás (`MasterType.Primary`, `UserStatus.Allowed`, `FileType.Public`) se escriben en el wire igual que su constante. Sin `@JsonValue`, Jackson deserializa por `Enum.name()` y **todo grant y revoke desde la pantalla respondía `400`** — mientras la *respuesta* sí decía `"Player"`, o sea que el contrato se contradecía consigo mismo. **Ningún test de ninguno de los dos lados podía encontrarlo**: los del backend llaman al service directo y los del frontend mockean el `fetch`; apareció en la revisión del diff y se confirmó ejecutando Jackson a mano. El nombre del rol entra además por **dos puertas** —el cuerpo JSON y el literal del `?q=` cuando alguien escribe `/role Admin`—, y la segunda falla más calladito: si no resuelve, matchea cero filas y parece una plataforma vacía, no un error. Por eso la equivalencia quedó fijada con un test que recorre las cuatro constantes en vez de deducida, con una asimetría deliberada: `"ADMIN"` es `400` en el cuerpo pero sí resuelve en la caja de búsqueda, porque una caja de búsqueda tiene que sobrevivir a que la escriban. **El `400` es más ancho que esta rebanada**: cualquier cuerpo malformado en cualquier endpoint dejó de ser `500`. Era el gemelo de `handleTypeMismatch`, que ya cubría path variables y query params; el cuerpo tenía el mismo agujero y nadie se había caído en él porque hasta ahora ningún enum difería.
