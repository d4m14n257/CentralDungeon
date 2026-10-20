---
adr: 5
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0005: UI library del frontend

## Decisión

**shadcn/ui + Tailwind**, se abandona MUI

## Por qué

La mayoría de los componentes se reconstruyen igual (varios quedaron a medias en el Next.js viejo), así que no hay costo de migración. Más liviano que la combinación duplicada MUI+Emotion+styled-components que arrastraba el proyecto, y hay un MCP que da acceso al código fuente real de cada componente.
