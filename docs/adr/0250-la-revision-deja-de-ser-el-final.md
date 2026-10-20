---
adr: 250
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0250: La revisión deja de ser el final de cada fase y pasa a ser una fase propia

> Amplía #177 y corrige el punto 5 de `plan-desarrollo.md` §6

## Decisión

**Seis fases, no cinco.** Entra **F4 Revisión** entre Admin y Comunidad, y las dos que venían detrás corren un número: Comunidad pasa a **F5** y Operación a **F6**. F1, F2 y F3 cierran **hechas y sin revisar**: conservan sus tests —una fase en rojo no cierra, eso no se mueve— y **entregan además su deuda de revisión escrita**, la lista de lo que no verificaron. F4 no construye producto: verifica el mapa de la interfaz, la matriz de roles, la integridad y la seguridad de las tres fases juntas, y termina con una revisión mano a mano del cliente.

## Por qué

F1.7 probó la revisión por fase y funcionó — encontró dos huecos de navegación reales—, pero encontró lo que podía: **revisó F1 contra F1**. Lo que de verdad se rompe son las costuras entre actores, y una costura no se puede mirar hasta que existen sus dos lados. El caso más claro es la matriz de roles: «quién puede qué» no tiene respuesta hasta que están los cuatro roles, así que revisarla en F1 es revisar un tercio y darla por buena. La seguridad tiene la misma forma —una prueba de IDOR necesita dos actores con derechos distintos, y la exclusión del vetado en la lectura de archivos que #206 dejó anotada necesita el veto, que es F3—. Y hay un recurso escaso que es la **atención del cliente**: gastarla tres veces sobre tres productos parciales y otra vez sobre el entero desperdicia justamente las pasadas que importan. **El costo se acepta explícito y no se disimula**: F1, F2 y F3 se entregan sin revisar, así que sus bugs viven más tiempo. Lo que lo acota es que la regla de los tests no se mueve, y que la deuda de revisión se escribe en el momento en vez de redescubrirse — un hueco anotado es una decisión, uno implícito es una sorpresa (§1). **Las referencias ya escritas no se reescriben**, misma regla que fijó #177: «F1.4 construyó X» es un hecho fechado; solo se actualiza lo que **promete** algo para una fase que cambió de número.
