---
adr: 174
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0174: Prettier, con la configuración del repo

## Decisión

Se adopta **prettier** para el frontend, con `.prettierrc.json`: sin punto y coma, comillas simples, ancho 140, coma final. `npm run format` y `npm run format:check`. **`src/components/ui/` queda excluido**: lo genera el CLI de shadcn y reformatearlo sería pelearse con el generador en cada componente nuevo. El backend no entra: prettier no formatea Java

## Por qué

Lo pidió la práctica, no la teoría: correr `npx prettier` sin configuración reformateó un archivo a comillas dobles y punto y coma —su default, no el estilo del repo— y hubo que revertirlo a mano. El estilo ya existía de hecho en todo el código; lo que faltaba era escribirlo en un archivo para que la herramienta lo respete en vez de imponer el suyo. El ancho de 140 sale de medir el código existente, no de una preferencia: el percentil 98 de las líneas estaba en 114 y las envolturas manuales ya rondaban ese ancho. Es formato, no reglas: `oxlint` sigue siendo el linter
