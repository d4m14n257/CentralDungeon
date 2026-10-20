---
adr: 110
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0110: Envoltorio de los formularios en modal

## Decisión

**Un `FormDialog` en `components/`** con título, descripción y confirmación al cerrar con cambios sin guardar. El diálogo de cada feature compone `FormDialog` + el formulario y **es el dueño de la mutación**: el formulario solo valida y llama `onSubmit`

## Por qué

Es la mitad que le faltaba a #106. El legacy tenía `ModalBase` y la idea era correcta —incluso preguntaba antes de cerrar un modal a medio llenar, que es lo único que se rescata—, pero el reparto estaba invertido: `EditTableForm` recibía `handleCloseModal` y `reloadAction`, hacía él mismo el `PUT` y decidía cuándo cerrarse. Por eso no se podía reutilizar fuera de un modal, que es exactamente lo que #106 quiere. Con la mutación en el diálogo, el mismo `GameTableForm` sirve en un modal y en una página. Y `FormDialog` no recibe entidades del dominio: si las recibiera no tendría por qué ser transversal.
