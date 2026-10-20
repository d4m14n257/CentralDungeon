---
adr: 222
estado: Aceptada
fecha: 2026-09-09
---

# ADR-0222: Cada contexto es dueño de un prefijo, y el chip dice dónde estás

## Decisión

**Tres cosas.** (a) **El contexto Jugador se muda a `/player/*`**, como Master y Admin ya tenían el suyo: `/player` (explorar), `/player/tables/:id`, `/player/applications`, `/player/my-tables`, `/player/my-tables/:id`. F2 hereda el prefijo para `/player/history`, `/player/files` y `/player/profile`. (b) **`/` deja de ser una pantalla** y pasa a ser un despachador: mira los contextos de la cuenta y reenvía a la home del que tenga. El retorno del OAuth, el onboarding y el 404 apuntan ahí y ninguno recalcula el destino. (c) **El chip del selector reporta el contexto de la URL**, no el que se eligió alguna vez. Las dos pantallas transversales —`/notifications` y `/help`— no son de ningún contexto y conservan el que traía quien las abrió; y solo se recuerda un contexto que la cuenta tenga. Como consecuencia el contexto Jugador estrena su `PlayerSectionNav`, que es el bug de navegación que la revisión de F1 había dejado abierto

## Por qué

Lo reportó el usuario probando con `master-1`: entró como master, escribió `/`, y la interfaz lo dejó explorar mesas **con el chip todavía diciendo «Master»**. No era un problema de seguridad y él mismo lo dijo —el backend autoriza endpoint por endpoint (#103, #121)— sino de que la interfaz afirmaba algo falso: el selector existe justamente para evitar ese cruce y ahí lo estaba tapando. La causa era que el chip leía un valor persistido en `localStorage` elegido quién sabe cuándo, mientras la navegación de abajo era la de otro contexto. **La decisión de fondo la tomó el usuario y es más amplia que el síntoma**: explorar mesas es del contexto Jugador, no de «cualquiera con sesión», así que las mesas dejan de colgar de la raíz y el contexto gana su prefijo. Con eso, `/` se queda sin contenido propio y el problema se disuelve por dónde venía: al entrar siempre caés en tu propia home y no hay forma de terminar en otro contexto sin haberlo pedido. **Lo que no se hizo, a propósito**: `/player` no exige el rol `Player`. Eso convertiría el contexto en autorización del lado del frontend, que es exactamente lo que #103 prohíbe, y cerraría el explorador a un master que quiere ver qué otras mesas existen. Lo que sí pide el rol es postularse, y eso ya se decide en la pantalla con el motivo escrito en el botón deshabilitado. El chip que dice «Jugador» a alguien sin ese rol no miente: dice dónde está, que es su único trabajo. Verificado con las 33 pruebas e2e en verde sobre las rutas nuevas, y con una aserción nueva en `help.spec.ts` que fija las dos mitades: un master ve solo la ayuda de master, y su chip dice «Jugador» en `/player` y «Master» en `/master`.
