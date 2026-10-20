---
adr: 181
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0181: Cómo se ejecuta cada rebanada: reparto en subagentes

## Decisión

**Contrato primero en el hilo principal** —endpoints, DTOs, rutas y `queryKeys`—, después **A1 Backend** y **A2 Frontend** en paralelo con alcance de directorio disjunto, después **A3 Verificación**, y el cierre —docs, ayuda, commit— de vuelta en el hilo principal. **A4 Revisor** opcional entre los constructores y A3, en las rebanadas pesadas. Detalle en `plan-desarrollo.md` §7

## Por qué

Vale para F1 a F5, no para una rebanada suelta. **El contrato va primero** porque el frontend no puede esperar a que el backend exista para empezar, y si adivina la forma del DTO adivina mal: sin contrato el paralelismo no ahorra tiempo, lo tira. **A3 no es "el que escribe los tests"**, y esa es la parte que más fácil se malinterpreta: la regla dura 7 pide que toda regla de negocio llegue con su test unitario *con* ella, escrito por quien la escribió — un constructor que entrega lógica pelada y deja que otro le ponga tests después produce tests que describen lo que el código hace, no lo que la regla exige, que es el bug que nadie encuentra. A3 aporta el nivel que ningún constructor cubre solo: concurrencia con Testcontainers, Playwright cruzando ambos lados, los cuatro estados de cada pantalla y el camino manual. **Los agentes no commitean**: un commit por agente rompe el punto 5 de las reglas de git, que pide un mensaje que describa todo lo que se hizo
