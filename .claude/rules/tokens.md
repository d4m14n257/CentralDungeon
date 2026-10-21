---
paths:
  - "design/**"
  - "frontend/src/styles/**"
---

# Tokens de diseño

> Se cargan al tocar `design/` o los estilos globales del frontend. El sistema visual completo es la skill `diseno`.

`design/build.py` es la **fuente de verdad de todo valor de diseño** (ADR-21 §1): paleta, tipografía, espaciados y radios. Escribe en `design/out/` —ignorado por git— el `theme.css` que se transcribe al `@theme` del frontend, más las previews de tokens, componentes y pantallas.

- **Cambiar un color es editar `build.py` y volver a correrlo**, nunca tocar el CSS generado ni `src/styles/globals.css` a mano.
- Cada corrida mide los 34 pares de contraste de texto y 6 gráficos no textuales (el detalle, en `diseno` §3) y **sale con código 1 si alguno cae por debajo de WCAG AA**: una paleta rota no se publica.
- Transcripción, después de cada cambio:

  ```bash
  python3 design/build.py
  { printf '@import "tailwindcss";\n\n'; cat design/out/theme.css; } > frontend/src/styles/globals.css
  ```

- Las clases de patrón (`styles/base.css`) sí se escriben a mano, y cada una se cataloga en `diseno` §5.c (ADR-21 §5).
- Todo cambio visual se mira en los dos temas, claro y oscuro.
