---
adr: 166
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0166: Primary y Secondary son vocabulario interno: no se muestran

## Decisión

En la interfaz una mesa tiene un **master** y sus **co-masters**. `Primary`/`Secondary` siguen siendo los valores de `masters.master_type` y los nombres del código, pero **ningún texto visible los usa**: el chip del primero dice "Master", ascender a alguien es "Hacer master a X", y la leyenda del diálogo de asignación es "El primero que agregues es el master y el resto queda como co-master". Aplica a toda pantalla, no solo a `/admin/tables`

## Por qué

Son términos del modelo de datos, no del juego: quien usa la aplicación dirige una mesa o ayuda a dirigirla, y nunca escuchó "Primary". Filtrarlos a la pantalla obliga a traducir mentalmente en cada uso y encima colisiona con el **otro** `Owner`/`Primary` del sistema —el rol de plataforma contra `masters.master_type`—, la confusión que #67, #71 y #89 ya habían tenido que desarmar una vez. La regla se escribe acá porque el próximo lugar donde aparezca la distinción (el detalle de mesa, `/admin/users`, las notificaciones) va a tener que resolverla igual, y la respuesta ya está dada: master y co-master
