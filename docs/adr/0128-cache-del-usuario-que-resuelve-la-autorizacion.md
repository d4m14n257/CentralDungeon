---
adr: 128
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0128: Caché del usuario que resuelve la autorización

## Decisión

**Caffeine en proceso**, vía Spring Cache. `expireAfterWrite = 60 s`, `maximumSize = 10 000`, más **`@CacheEvict` explícito** al bloquear a alguien o cambiarle los roles

## Por qué

#122 hace que cada request lea roles y `status` de la base, lo que sin caché convierte cien peticiones de un usuario en cien consultas. Caffeine es una caché en el heap del proceso: sin servidor aparte, sin red, con desalojo por tiempo y tamaño (política W-TinyLFU). **El TTL es la ventana de revocación**, y por eso son 60 s y no cinco minutos: es el equilibrio entre no golpear la base y no dejar suelto a un admin degradado o a alguien recién baneado — con el rol `Owner` dando acceso a la auditoría (#92) y al borrado físico (#66), cinco minutos es demasiado. La evicción explícita hace que en la práctica el efecto sea inmediato, y el TTL queda como red de seguridad para cualquier camino que se olvide de evictar. **Limitación conocida y aceptada**: la caché es **por JVM**, así que con más de una instancia dos cachés pueden discrepar y `@CacheEvict` solo limpia la local — la misma restricción que el broker STOMP en memoria (#101). Con una instancia no molesta; con dos hace falta caché compartida o el TTL pasa a ser la única garantía.
