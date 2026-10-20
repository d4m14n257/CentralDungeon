---
adr: 67
estado: Aceptada
fecha: 2026-08-29
modificada-por: [89]
---

# ADR-0067: Roles de la plataforma

## Decisión

**Son cuatro: `Player`, `Master`, `Admin` y `Owner`.** `Owner` es el rol máximo —el cliente, puede todo— y **no** tiene relación con `masters.master_type = 'Owner'`, que es un sub-rol dentro de una mesa

## Por qué

Amplía #37, que solo listaba tres. El cuarto faltaba en el schema heredado y en el seed (skill `arquitectura` §4.6), así que había operaciones —como el borrado físico de archivos (#66)— sin ningún rol que las pudiera ejecutar. Sigue valiendo que los roles son **acumulables y sin jerarquía** (#37): `Owner` puede todo por definición, no porque herede de `Admin`. **En el código los dos "owner" no pueden llamarse igual**: conviene `PlatformRole.OWNER` para el global y `MasterType.OWNER` para el de la mesa, o directamente renombrar el segundo.
