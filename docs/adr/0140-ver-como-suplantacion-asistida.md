---
adr: 140
estado: Aceptada
fecha: 2026-08-29
modifica: [43]
---

# ADR-0140: "Ver como" — suplantación asistida

## Decisión

**Un admin puede entrar viendo lo que ve otra persona y actuar por ella, con cuatro límites duros.** (a) **Nunca sobre `Admin` ni `Owner`** — sería escalada de privilegios. (b) **Todo lo irreversible queda bloqueado**: vetar (#39), cancelar mesa (#27) y confirmar comentario (#40). (c) **Todo lo que toque comentarios queda fuera por completo** — borradores, diálogo de comentar, comentarios pendientes. (d) **Motivo obligatorio** al iniciar. Se registra en `impersonation_sessions`, con **caducidad automática a los 30 minutos**, y `audit_logs` gana `impersonation_id`. **La persona es notificada al inicio de la sesión**, con el motivo y sin el nombre del admin

## Por qué

El límite (c) no es una precaución, es lo que hace que la función sea admisible. `comment_drafts` **guarda el autor** a propósito: el borrador tiene autor y solo se vuelve anónimo al confirmarse. Un "ver como" sin ese recorte le mostraría a un admin quién escribió qué antes de anonimizarse, y eso anula #43 —marcada **requisito duro del cliente, no negociable**—, contradice #45, que dice que la autoría es la **única** excepción a la visibilidad total del admin, y burla #92, que excluye `comments` y `comment_drafts` de la auditoría justamente para no guardar autor y contenido juntos. Sobre el registro: se pidió que no constara **qué** admin. Se separó **qué se guarda** de **qué se muestra** — `impersonation_sessions.admin_id` se guarda siempre y el `Owner` lo ve en su panel; al afectado y a cualquier otro se les muestra "modificado por un administrador", sin nombre. Sin eso, la función más poderosa del sistema quedaría sin responsable: si un admin hace algo grave con la cara de otro, no habría a quién preguntarle. Se descartó **solo lectura** porque no cubre el caso que originó el pedido —asistir, no mirar— y **actuar sin restricciones** porque permitiría vetar o cancelar una mesa con la identidad de otro, y el afectado solo vería "un administrador". `audit_logs.updated_by` sigue siendo la identidad bajo la que ocurrió el cambio, así que "qué cambió Ana" se sigue respondiendo igual; el rastro del admin cuelga de `impersonation_id`, que es aditivo.
