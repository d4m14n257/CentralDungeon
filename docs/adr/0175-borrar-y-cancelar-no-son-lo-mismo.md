---
adr: 175
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0175: Borrar y cancelar no son lo mismo: la línea es si alguien más la vio

## Decisión

**Una mesa se borra solo mientras nunca fue pública** —`Unassigned`, `Preparation`, `ChangesRequested`— **y nadie se postuló**. Todo lo demás se **cancela**, con motivo escrito, y queda visible como cancelada. El borrado es lógico (#25): `status = 'Deleted'` + `deleted_at`, con cascada explícita en el service a las filas de `masters` y a las postulaciones, todas con la misma marca de tiempo y en una transacción; la transición queda igual en `table_status_changes`, así que el rastro sobrevive aunque la mesa no. Lo hace el `Primary` o un admin —los mismos que cancelan—, `DELETE /api/v1/game-tables/{id}` devuelve `204`, y **toda lectura la trata como inexistente**: `getEntityById` responde `404`, no `403`. **Regla general que se estrena acá**: cada etapa que agrega una entidad decide y construye su borrado en la misma etapa, no "más adelante" (`plan-desarrollo.md` §1)

## Por qué

La pregunta que lo abrió fue concreta —se crea una mesa y al final no se hace, ¿no falta el delete?— y la respuesta no es "agreguemos un delete" sino **dónde está la línea**. Cancelar una mesa que otras personas vieron, a la que se postularon o en la que jugaron no es burocracia: es el registro de algo que pasó, y borrarlo sería falsificarlo — la ventana de visibilidad de perfiles (#44) y el karma (#97) cuelgan de ese cierre. Un borrador que nadie vio no tiene nada de eso: obligar a cancelarlo llena la lista del master de mesas "canceladas" que nunca existieron para nadie, y encima le pide justificar algo ante nadie. La condición de "sin postulaciones" es la que cierra el hueco: una mesa en `Preparation` no debería tener candidatos, pero si alguna vez los tiene, el estado deja de ser la única señal y manda el hecho. Lógico y no físico porque es lo que #25 ya decidió para todo el sistema, y porque una mesa borrada sigue teniendo filas que la referencian. **Qué borrado falta y dónde va** (fases de #177): retirar una postulación (**F2**, con el resto del ciclo de postulación), baja de un tag o sistema del catálogo (**F3**, ya diseñada en #59), borrado de archivos (**F1** el lógico, **F5** el físico del owner, #66), y baja de una cuenta (**F5**, #83, #84)
