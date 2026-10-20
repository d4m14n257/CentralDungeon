---
adr: 82
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0082: createdat en los comentarios

## Decisión

**Los comentarios llevan `created_at`. Para que eso no delate al autor, la cuota antispam se guarda como token opaco, no como la tupla en claro**

## Por qué

Cierra **M15**. Guardar `(autor, comentado, mesa)` en claro en una tabla de cuota —como se había planteado— **incumplía #43 por sí solo**, con o sin timestamps: esa tabla *es* el registro de quién comentó sobre quién. Con `HMAC(secreto, autor+comentado+mesa)` y un `UNIQUE` sobre el token, la restricción antispam se sigue verificando y la tabla deja de contener nada legible. **El secreto vive fuera de la base** (variable de entorno o gestor de secretos): un volcado de la base, por sí solo, no permite reconstruir nada. Con el token opaco los timestamps dejan de importar, así que `comments.created_at` puede ser normal.
