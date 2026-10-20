---
adr: 96
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0096: Parámetros del karma

## Decisión

**`W = 20`** (peso del prior, en comentarios equivalentes) y **`H = 12 meses`** (semivida del decaimiento), sobre un prior `m = 0.8`

## Por qué

Cierra la parte de parámetros de **M11**. Con `W = 20` un negativo aislado cuesta ~380 puntos y hacen falta tres para bajar de 7000: castiga un patrón, no un mal día. Con `H = 12 meses`, un negativo de hace un año pesa la mitad, lo que permite recuperarse sin borrar el historial. Ambos son números de configuración, no constantes de código: ajustarlos no requiere migración.

## Detalle: la fórmula del karma

Movido acá desde la sección «Karma — propuesta de cálculo» del antiguo `decisiones.md`: es el razonamiento que cerró M11, y lo necesita quien construya el karma en F5.


Cierra **M11**. Fórmula y parámetros decididos (#96–#99). Lo que sigue es implementarlo.

### Qué se revisó

| Familia | Qué es | Veredicto |
|---|---|---|
| **Beta Reputation System** (Jøsang & Ismail, 2002) | Cuenta evidencia positiva y negativa como parámetros de una distribución beta: `α = p+1`, `β = n+1`, reputación `E = α/(α+β)`. Incluye un *forgetting factor* para envejecer la evidencia vieja | **Es la base.** Está pensado exactamente para esto: reputación a partir de valoraciones de pares, con pocos datos y comportamiento que cambia en el tiempo |
| **Promedio bayesiano con prior** | `(C·m + Σ rᵢwᵢ) / (C + Σ wᵢ)` — el prior `m` con peso `C` actúa como valoraciones fantasma hacia las que el puntaje se encoge cuando hay poca evidencia | **Es la forma práctica del anterior**, y permite anclar el prior en 8000 de forma natural |
| **Wilson score (cota inferior)** | Cota inferior del intervalo de confianza de una proporción binaria. Lo usa Reddit para ordenar comentarios | **Descartado.** Sirve para *ordenar* penalizando lo poco votado, no para dar un puntaje absoluto, y no admite un prior en 0.8 — empuja todo hacia abajo, que no es lo que queremos con un usuario nuevo |
| **Elo / Glicko** | Puntaje por enfrentamientos entre pares | **Descartado.** Necesita partidas con ganador y perdedor. Acá nadie compite contra nadie |

### La fórmula propuesta

Cada comentario aprobado aporta un valor `v` y un peso `w`:

| `karma_impact` | Significado | Valor `v` |
|---|---|---|
| positivo | "me gustó jugar con esta persona" | `1.0` |
| neutro | "jugamos, sin nada que destacar" | `0.8` — **igual al prior** |
| negativo (`Reported`) | "me desagradó por su actitud" | `0.0` |

El peso decae con la antigüedad: `w = 2^(−antigüedad / H)`, con `H` = semivida.

```
karma = 10000 × ( W·m + Σ wᵢ·vᵢ ) / ( W + Σ wᵢ )

  m = 0.8    prior, el 8000 de arranque
  W = 20     peso del prior, en "comentarios equivalentes"
  H = 12 meses
```

**Por qué el neutro vale 0.8 y no 0.** Si el neutro no contara como evidencia, alguien con 20 neutros y 1 negativo quedaría igual que alguien con 1 solo negativo y nada más — y no es lo mismo. Valiendo exactamente el prior, el neutro **no mueve el promedio pero sí acumula confianza**, y por eso amortigua un negativo aislado. Es la diferencia entre 7805 y 7619 en la tabla de abajo.

### Cómo se comporta (con W=20, H=12 meses)

| Situación | Karma |
|---|---|
| Usuario nuevo, sin comentarios | **8000** |
| 1 positivo | 8095 |
| 3 positivos | 8261 |
| 10 positivos | 8667 |
| 30 positivos | 9200 |
| 1 negativo | 7619 |
| 3 negativos | 6957 |
| 10 negativos | 5333 |
| 50 negativos | 2286 |
| 20 neutros y nada más | **8000** (no mueve) |
| 20 neutros + 1 negativo | 7805 |
| 10 positivos + 3 negativos | 7879 |
| 3 negativos de hace 12 meses | 7442 (vs 6957 si fueran de hoy) |
| 3 negativos de hace 24 meses | 7711 |

Cumple los cinco criterios que se habían fijado: **rendimientos decrecientes** (es un promedio, el positivo 30 mueve menos que el 3º), **peso por antigüedad** (la semivida), **proporción y no total**, **resistencia a pocos datos** (con 0 comentarios queda exactamente en 8000), y **explicable** en una frase: *"partís de 8000 y cada comentario tira del promedio; los viejos pesan menos"*.

### Consecuencias operativas

1. **El karma es un valor derivado, no un contador.** Como los pesos decaen con el tiempo, cambia **sin que ocurra ningún evento**. No se puede incrementar al aprobar un comentario: hay que **recalcularlo periódicamente** (un job nocturno). `users.karma` pasa a ser una proyección cacheada, no la fuente de verdad — la fuente son las filas de `comments`.
2. **Se calcula sin saber quién comentó.** Solo necesita destinatario, `karma_impact` y `created_at`, que es justo lo que `comments` guarda (#43). El anonimato no estorba.
3. **Solo cuentan los aprobados** (#51).
4. **Los extremos son asintóticos.** Llegar a 0 exige decenas de negativos sostenidos; 10000 exige lo mismo en positivo. Es deseable —una mala noche no arruina a nadie, un patrón sí— pero significa que el rango real es más angosto que 0–10000. Coherente con #74: el número es una señal para los admins, no un disparador.

### Cerrado

1. **`W = 20`**, **`H = 12 meses`** (#96).
2. **Recálculo**: al aprobar un comentario, para esa persona; y un job semanal nocturno para todos (#97).
3. **La asistencia queda fuera del cálculo** y se muestra aparte (#98).
4. **Se muestra el puntaje y los comentarios**, sin desglose agregado (#99).
