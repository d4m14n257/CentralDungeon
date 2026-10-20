---
adr: 165
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0165: Asignar masters en /admin/tables: el orden de los chips es el rol

## Decisión

El diálogo de "Asignar masters" deja de pedir ids a mano — el límite conocido que #163 documentó — y pasa a componer el buscador de personas: se busca, se elige, y cada elegido entra como chip. **El primero es el Primary y el resto Secondary; tocar el chip de un Secondary lo asciende**, y el que era Primary baja a Secondary en el mismo movimiento. Quitar al Primary asciende al que seguía. El endpoint que lo alimenta es `GET /api/v1/users/search`, **restringido a `Admin`/`Owner`**, y nunca ofrece a alguien que no esté `Allowed`. El diálogo vive en `routes/admin/`, no en `features/tables/`

## Por qué

Una mesa tiene exactamente un Primary (`modelo-datos.md` #73), así que la interfaz tiene que expresar "uno entre varios" sin un segundo control: una lista ordenada donde el primer lugar es el cargo lo dice sin explicarlo, y ascender es mover al frente. Alcanza con un clic y no hay estado inválido posible — no existe "dos Primary" ni "ningún Primary" mientras haya alguien elegido. El buscador queda admin-only porque el único consumidor de hoy es una pantalla de admin, y un directorio de personas no se abre más de lo que pide quien lo usa; cuando el Primary de una mesa necesite agregar un Secondary desde `/master/tables/:id`, ampliarlo es una decisión explícita y no un efecto secundario. Vive en `routes/` porque compone dos dominios —`users` pone el buscador, `tables` la mutación— y una feature nunca importa de otra (skill `arquitectura` §3.1.5); es la misma razón por la que las pestañas de `/master/tables/:id` están ahí al lado. Sin `react-hook-form` ni zod, a diferencia del resto de los diálogos: no hay ningún campo que validar, solo una lista con orden
