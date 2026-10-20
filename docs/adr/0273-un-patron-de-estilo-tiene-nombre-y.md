---
adr: 273
estado: Aceptada
fecha: 2026-09-28
modificada-por: [280]
modifica: [118, 130, 261]
---

# ADR-0273: Un patrón de estilo tiene nombre, y lo visual tiene su skill

> Precisa #118, #130 y #261

## Decisión

**Una combinación de utilidades que cumple un papel de diseño no se escribe literal en una pantalla**: tiene un nombre y se define una sola vez. Si es solo estilo, es una clase de `styles/base.css` en `@layer components` con `@apply` sobre los tokens (`base.css` abre con `@reference "./globals.css"`, porque `globals.css` se sobrescribe al transcribir el tema); si tiene estructura, props o comportamiento, es un componente de `components/`. Se crea **a la segunda aparición** y las dos pantallas pasan a usarlo. El catálogo inicial: `.page-title`, `.section-title`, `.section-label`, `.row-actions`, `.list-divided`, `.list-divided-bare`, `.inline-error` y el componente `PageHeader`. **`DataTable`, `CollapsibleSection` y `FileCard` ponen `.row-actions` ellos mismos**: una pantalla devuelve solo el fragmento de `IconAction`, y las acciones van en una línea, sin wrap. Todo esto vive en la **skill nueva `diseno`**, que además recibe §3 (sistema de diseño) y §5/§5.b (componentes, responsive) de `frontend-diseno.md` con su numeración; el catálogo es §5.c. El documento se queda con principios, navegación, sitemap y wireframes

## Por qué

Lo encontró el usuario revisando #272: pidió las acciones de fila en una fila y sin wrap, lo probó en `/admin/users`, y esperaba que el cambio se viera en todas las tablas — no pasó nada, porque cada pantalla escribía su propio `<div className="flex …">`. El barrido encontró lo mismo en todas partes: el título de página escrito a mano 25 veces, la etiqueta en mayúsculas 25, la lista con divisores 26, la caja de error de los diálogos 8, la cabecera de pantalla 7 veces solo en admin. **#261 ya había resuelto esto para un componente** (el décimo badge de estado); lo que faltaba era la regla general y el lugar donde leerla antes de escribir, y el usuario pidió explícitamente que fuera una skill de diseño, para que no se repita. Clase y no componente para lo que es solo estilo, porque un componente que envuelve un `h1` para ponerle tres clases es una capa sin trabajo; y `@layer components` y no `@utility`, porque así una utilidad escrita al lado sigue ganando y un ajuste puntual no pide una variante. **Deuda anotada, no resuelta**: `text-[11px]` en `NotificationBell` y `WizardSteps` es un valor suelto fuera del `@theme`
