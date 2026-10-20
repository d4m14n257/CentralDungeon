---
adr: 258
estado: Aceptada
fecha: 2026-09-18
---

# ADR-0258: Resolver un ítem no exige tenerlo reservado: exige que no lo tenga otro

> Corrige la regla de skill `arquitectura` §4.5

## Decisión

`AdminQueueClaimRule.requireNotHeldByAnother`: pasa si nadie lo tiene **o** si lo tiene el actor; **`409 ITEM_ALREADY_CLAIMED`** solo si lo tiene otro. Resolver algo libre es una reserva implícita. `ITEM_NOT_CLAIMED` no existe: ninguna rama puede emitirlo.

## Por qué

§5 decía «resolver un ítem exige tenerlo reservado» y se implementó así primero. **Rompió `/admin/requests`**, que F3.2 entregó con Aprobar y Rechazar y **ninguna forma de reservar**: cada resolución desde esa pantalla respondía `409` por una reserva que la pantalla no podía ofrecer. No fue un problema de tests — la pantalla quedó inutilizable en la aplicación real. **Chocaron dos diseños escritos en momentos distintos**: #176 le da a los pedidos su pantalla propia, y §5 asumía que la bandeja era el único lugar donde algo se resuelve; la lectura estricta volvió la bandeja obligatoria para un flujo que nunca pasó por ella. Lo que #100 compra es «si lo toma uno, baja para todos», así que lo que hay que rechazar es actuar sobre lo que **otro** tiene — un link viejo, una segunda pestaña, una bandeja sin refrescar. **La consistencia nunca dependió de esta regla**: dos admins resolviendo la misma fila libre se serializan con el lock pesimista de #256 y el segundo recibe «ya estaba resuelto». Es sobre no pisarle el trabajo a un colega, no sobre corrección. **Y da vuelta el principio 2 en la bandeja**: toda fila visible está libre o ya es del lector —la del colega la filtra `claimed_by IS NULL OR = :actor`—, así que esconder las resoluciones escondería una acción que funciona, que es el mismo pecado que ofrecer una que no. **Lo encontró Playwright y no los tests ni la revisión**: los tres filtros anteriores verificaron que la regla estuviera bien *aplicada*, y lo estaba; lo que estaba mal era la regla.
