---
adr: 53
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0053: Qué es cada catálogo, y qué significa parentid

## Decisión

**`systems`** = qué sistema de juego usa la mesa (D&D, 20roll…). **`tags`** = etiquetas para filtrar en la pantalla de inicio (campaña corta, pública…). **`platforms`** = dónde se juega (Roll20…). **`parent_id` no es una taxonomía: es un grupo de sinónimos.** El padre es el término canónico y los hijos son formas alternativas de escribir lo mismo

## Por qué

skill `arquitectura` §4.7 lo describía como "jerarquía de catálogos" con "navegación de árbol", y eso llevaba a implementar lo que no es. El propósito real es que "D&D", "Dungeon&Dragons" y "DANDD" sean **el mismo** tag: quien busca escribe el que conoce y encuentra las mesas etiquetadas con cualquiera de los otros. El CTE recursivo que quedó comentado en `legacy/backend-node/src/app.ts` confirma la intención — sube desde un tag hasta su raíz (`WHERE tag_id IS NULL`) para resolver el canónico. Nunca se conectó a un endpoint.
