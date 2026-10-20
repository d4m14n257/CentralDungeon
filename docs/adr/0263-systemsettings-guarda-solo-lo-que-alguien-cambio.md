---
adr: 263
estado: Aceptada
fecha: 2026-09-21
modifica: [141]
---

# ADR-0263: systemsettings guarda solo lo que alguien cambió, y la categoría Texts no se construye

> Precisa #141

## Decisión

El catálogo de ajustes es un `enum` en el código (`SettingKey`) con el valor por defecto, el rango y si el cambio es retroactivo; **la tabla guarda overrides y nada más**: no hay fila para un ajuste que nadie tocó, no hay seed, y cambiar un default es editar el `enum`. De las tres categorías de #141 se construyen **dos** —`Business` y `Limits`—; `Texts` no. Y de los ocho valores que #141 enumera se mudan **cuatro**: los que hoy tiene algo que los lea

## Por qué

**Sin seed, un alta y una base recién vaciada se comportan igual**, y cambiar un default deja de ser una migración. Con seed, cada valor por defecto sería un dato que hay que migrar el día que cambia, que es justo lo que #141 evitaba al elegir clave-valor en vez de una columna por ajuste. El `enum` como catálogo es además lo que deja que la pantalla muestre «valor actual y por defecto» sin inventarse de dónde sale el segundo. **`Texts` se cae por #197**, que es posterior a #141: desde que el backend no escribe ninguna frase que lea una persona —manda un código y sus parámetros, y el frontend la arma en el idioma elegido—, un ajuste de tipo texto sería una frase guardada en un solo idioma y renderizada cruda, o sea la regla dura 18 rota por diseño. Declarar la constante vacía habría sido el huérfano que #255 se negó a crear en F3.2. **Y cuatro valores y no ocho por lo mismo**: el karma inicial y la ventana de decaimiento (#97) y la ventana del feedback (#94) son de subsistemas que construye F5, y la justificación por defecto del rechazo automático (#34) dejó de ser una frase del backend el día de #197 — hoy es el código `TABLE_FULL`. Un ajuste que nada lee es una perilla que no hace nada
