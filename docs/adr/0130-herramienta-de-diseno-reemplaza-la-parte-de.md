---
adr: 130
estado: Aceptada
fecha: 2026-08-29
modificada-por: [273]
modifica: [6, 118]
---

# ADR-0130: Herramienta de diseño — reemplaza la parte de #118 que nombraba a Figma

## Decisión

**Claude Design (`claude.ai/design`), no Figma.** El sistema de diseño se construye como previews HTML + Tailwind, se publica en un proyecto de design system con la herramienta `DesignSync` y se revisa en el navegador. **Figma sale de `.mcp.json`** (corrige #6). El principio de #118 no se toca: manda el diseño, y el `@theme` de `globals.css` se transcribe desde ahí

## Por qué

#118 daba por disponible una herramienta que no lo está: el MCP de Figma exige asiento **Dev o Full en plan pago**, tanto el remoto (`mcp.figma.com`) como el servidor local de Figma Desktop, y este proyecto no lo tiene. Se verificó en la práctica — Figma Desktop instalada y corriendo, pero la opción *Enable local MCP server* no disponible sin plan. Sin MCP, Figma quedaba como un dibujo del que había que copiar valores a mano, que es **exactamente la divergencia que #118 existía para evitar**. Lo que decide a favor de Claude Design no es el costo (cero adicional, se autoriza con `/design-login`) sino que el artefacto de diseño **ya está hecho del material del que sale el código**: HTML y clases de Tailwind. Con Figma, diseño y código eran dos representaciones a mantener alineadas a mano; acá la transcripción al `@theme` es mecánica y la divergencia no tiene dónde aparecer. **Lo que se pierde, explícitamente**: no hay lienzo vectorial, así que no se exploran ideas moviendo cajas con el mouse. Se acepta porque las cinco pantallas ya están resueltas como wireframes en `diseno` §4 y lo que queda por decidir —color, tipografía, espaciado— se juzga mejor sobre el componente real que sobre un rectángulo gris. Se evaluaron y descartaron: **Penpot** (vectorial y gratis, pero sin integración verificada, así que reintroduce la copia manual de valores), **Figma Starter sin MCP** (mismo problema, agravado) y **diseñar directo en el código** (invierte #118 y disuelve E0.5 dentro de E1).
