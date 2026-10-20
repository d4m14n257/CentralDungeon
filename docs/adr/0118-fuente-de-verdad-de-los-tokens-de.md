---
adr: 118
estado: Aceptada
fecha: 2026-08-29
modificada-por: [130, 273]
---

# ADR-0118: Fuente de verdad de los tokens de diseño — la herramienta cambia en #130

## Decisión

~~**Manda Figma.**~~ **Manda el diseño, no el JSX** — qué herramienta lo aloja lo fija #130. Los colores, espaciados, radios y tipografías se deciden en el diseño y se transcriben al bloque `@theme` de `globals.css`. Si un valor no está en el tema, primero se agrega al diseño

## Por qué

El principio se mantiene entero: lo único que cambió en #130 es dónde vive el diseño. El diseño es donde las decisiones visuales se toman de verdad. Sin una dirección declarada, cada valor suelto escrito en el JSX crea una divergencia que nadie sabe cuál de las dos partes tiene que corregir. La consecuencia práctica es la regla de #109 llevada hasta el final: en el JSX se usan clases de Tailwind que refieren tokens del tema, nunca valores arbitrarios — un `bg-[#7c3aed]` es la señal de que falta un token.
