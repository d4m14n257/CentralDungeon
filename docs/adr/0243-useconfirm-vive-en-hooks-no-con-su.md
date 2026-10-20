---
adr: 243
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0243: useConfirm vive en hooks/, no con su diálogo

## Decisión

El hook y su `ConfirmContext` pasan a `hooks/useConfirm.ts`; `components/ConfirmDialog.tsx` queda exportando solo el provider. Barrido de los demás: `useAuth` y `useContextStore` quedan donde están

## Por qué

Un hook en `components/` contradice el árbol de skill `arquitectura` §3.1, que reserva `hooks/` para eso. De paso apaga el aviso de oxlint `only-export-components`: un módulo que exporta un componente **y** un hook pierde el fast refresh. El barrido encontró además que el inventario de hooks de los dos documentos estaba desactualizado — listaba `useScrollOnHash`, que murió con #231, y `useTableSelection`, que nunca se construyó, y omitía seis que sí existen. `useAuth` **no** se movió: vive en `providers/`, que es donde §3.1 pone un Context global junto a su provider, y `useContextStore` es un store de Zustand, que tiene su propia capa.
