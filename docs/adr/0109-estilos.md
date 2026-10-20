---
adr: 109
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0109: Estilos

## Decisión

**Clases de Tailwind en el JSX, variantes con `class-variance-authority` y composición con `cn()`.** Sin CSS-in-JS, sin archivos CSS por componente, sin objetos de estilo en JS

## Por qué

El frontend viejo tenía los estilos como objetos JavaScript en `styles/*.js`, además de cuatro motores conviviendo. Con Tailwind el estilo se lee en el mismo lugar donde está el marcado, los tokens salen del `@theme` (#59 del sistema de diseño) y `cva` cubre lo que el objeto JS resolvía —variantes por props— sin salir de CSS. Es el patrón que shadcn/ui ya usa internamente, así que no hay dos formas de hacer lo mismo.
