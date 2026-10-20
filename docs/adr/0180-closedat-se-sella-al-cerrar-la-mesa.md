---
adr: 180
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0180: closedat se sella al cerrar la mesa

## Decisión

Al entrar en `Finished` o `Canceled`, `GameTableService` sella `game_tables.closed_at`. La columna se mapea en la entidad, junto con `permitted`, que estaba en el mismo estado

## Por qué

Es una regla de skill `arquitectura` §4.5 desde #44 y **hoy no se cumple**: E2 construyó la máquina de estados completa pero `GameTable` dejó `closed_at` sin mapear —lo dice su propio comentario de clase— y nada la escribe. No es cosmético: `closed_at` arranca la ventana de visibilidad de perfiles de #44 y es lo que distingue una mesa cerrada ayer de una cerrada hace un año. Se cierra en F1 porque F1 es la mesa completa **hasta el final**, y una fase que entrega el cierre de la mesa sin sellar la fecha del cierre no lo entregó
