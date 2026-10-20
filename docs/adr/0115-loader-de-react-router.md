---
adr: 115
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0115: loader de React Router

## Decisión

**No se usan.** Cada pantalla pide sus datos con TanStack Query desde el componente

## Por qué

La recomendación habitual es combinarlos —el loader adelanta el fetch, Query cachea— y es buena con SSR. Acá no hay SSR, la aplicación entera está detrás de login y cada pantalla ya define su estado de carga con skeleton. Adoptarlos obliga a inyectar el `queryClient` en el router y a declarar la misma query en dos lugares, a cambio de ganar milisegundos en navegaciones frías. **Es reversible por pantalla**: si alguna vez se mide un waterfall real, se agrega el loader ahí sin tocar el resto.
