---
adr: 98
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0098: Asistencia y karma

## Decisión

**La asistencia no entra en el cálculo del karma. Se muestra como métrica aparte, junto al perfil**

## Por qué

Mezclar dos señales distintas en un solo número lo vuelve imposible de explicar, y explicable era uno de los criterios de #74 y M11. Separadas, un master ve "karma 7800" y "faltó a 3 de 10 sesiones" y saca su propia conclusión, que es más información que un número que ya las combinó por él. Si más adelante se quiere fusionar, la asistencia ya está registrada (#36) y no hay que rehacer nada.
