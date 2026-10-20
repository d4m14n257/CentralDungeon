---
adr: 167
estado: Reemplazada
fecha: 2026-09-02
reemplazada-por: [231]
---

# ADR-0167: Una pantalla de ayuda, /help, para toda la comunidad — reemplazada por #231: la ayuda pasó a ser un diálogo y /help dejó de ser una pantalla

## Decisión

**Pantalla propia, no un tooltip ni un `README`**: `/help` explica cómo buscar, qué son los contextos y qué puede cada uno, qué significan los nueve estados de una mesa, cómo funcionan las postulaciones y qué es un master frente a un co-master. Se entra desde el menú de la cuenta, está en los cuatro contextos, y **pide sesión** —es para quien usa el sistema, no una landing—. Todo su texto sale de `locales/es/help.json` y no tiene una sola query: es contenido. El buscador la enlaza desde su ayuda corta, con `helpPath()` y no con el patrón relativo de `paths`, que resolvía a `/admin/tables/help`

## Por qué

El lenguaje de #164 es potente y **no se adivina**: nadie descubre solo que existen `/campo`, `/or` y las comas. Sin un lugar donde leerlo, o el buscador se convierte en una caja de texto que la mitad de la gente usa a ciegas, o cada pantalla repite media explicación en una leyenda que nadie termina de leer. Una pantalla sola, enlazada desde donde hace falta, deja la leyenda corta y el detalle a un clic. Y una vez que existe el lugar, lo que corresponde poner ahí no es solo el buscador: la comunidad tiene cuatro roles acumulables y una mesa con nueve estados, y eso tampoco estaba escrito en ningún lado que un jugador pudiera leer. La pantalla crece con cada etapa; hoy cubre lo que ya se puede hacer, y lo dice
