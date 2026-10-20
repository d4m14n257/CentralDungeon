---
adr: 186
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0186: Sanitizar el texto enriquecido con la librería de OWASP

## Decisión

Se agrega **`owasp-java-html-sanitizer`** y un `RichTextSanitizer` en `common/text/` con una **lista blanca**: `p`, `br`, `strong`, `em`, `u`, `s`, `code`, `pre`, `blockquote`, `h1`–`h3`, `ul`/`ol`/`li` y `a` con `href` `http(s)` y `rel=nofollow`. Todo lo demás cae, incluidos `img`, `style` y cualquier `on*`. Se aplica **al guardar y al servir** sobre `description`, `permitted` y `requirements` (#62). Nulo entra, nulo sale; y lo que se sanitiza hasta quedar vacío vuelve nulo, porque no es contenido

## Por qué

Es la superficie de XSS más directa del sistema y la primera fase que estrena un editor. Escribir el escapado a mano es la forma habitual de publicar un XSS: hay que acertarle a las entidades, a los protocolos de `href`, a los atributos de evento y a los comentarios condicionales, y cualquiera de los cuatro alcanza. **Lista blanca y no lista negra** porque una etiqueta que nadie previó tiene que ser inofensiva por defecto. **Al servir además de al guardar** porque una fila escrita antes de que existiera esta puerta —o por cualquier camino que la saltee— llega igual al navegador, y sanitizar de un solo lado deja esa puerta abierta para siempre. El costo es una dependencia más; la alternativa era confiar en que ningún `INSERT` futuro esquive el service
