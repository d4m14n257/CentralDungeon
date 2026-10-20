---
adr: 182
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0182: La entidad de systems se llama GameSystem

## Decisión

**`GameSystem`, no `System`.** Igual que `GameTable` no se llama `Table`

## Por qué

Una clase `System` en `com.centraldungeon.catalogs` tapa `java.lang.System` para todos los archivos vecinos del paquete: cualquier `System.out` de ahí adentro dejaría de compilar o, peor, resolvería a la entidad. Es exactamente el motivo por el que `GameTable` ya no se llama `Table` (choque con `jakarta.persistence.Table`), así que el proyecto ya tenía el precedente y la convención. el documento de F1 (§F1.1, borrado en F4.0 y conservado en git) la nombra `System` porque describe la tabla, no la clase
