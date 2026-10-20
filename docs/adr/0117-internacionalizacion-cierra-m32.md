---
adr: 117
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0117: Internacionalización — cierra M32

## Decisión

**Entra desde la primera etapa.** Ningún string visible se escribe en el JSX: todo pasa por `t('espacio.clave')` con JSON en `locales/<idioma>/<espacio>.json`, un espacio por feature. En v1 solo existe `es`

## Por qué

Cierra M32 por la opción (a). La comunidad es de LatAm y algo de Europa (#22), así que la intención de tener inglés algún día es real, y con eso la cuenta se decide sola: hecho desde el principio cuesta una indirección por string; retrofitearlo significa recorrer cada componente ya escrito. Lo que queda explícitamente prohibido es el término medio que ya se intentó una vez: el legacy tenía `english.xml` y `spanish.xml` vacíos, un helper a medio escribir y ningún texto realmente traducido. O todo texto nace en el archivo de idioma, o no se instala nada.
