---
name: nuevo-componente-react
description: Scaffolds a new React component or page using shadcn/ui, Tailwind, and TanStack Query, inside its feature folder, following CentralDungeon's frontend architecture. Use when adding or finishing a component/page in frontend/ (React + Vite).
---

# Nuevo componente/página React

**Las reglas no están acá**: son las «reglas fijas» de la skill `arquitectura-frontend`, y se aplican todas. Esta skill es el procedimiento, en orden. Si la regla que hace falta no está en el resumen, se lee la sección de su referencia (`arquitectura-frontend` §3.x).

## Pasos

1. **Leer `docs/frontend-diseno.md` §5**, el inventario de componentes. Si lo que se va a escribir ya existe —un badge de estado, un diálogo con motivo obligatorio, un selector—, se reusa (#261).
2. Si reconstruye algo que existía, revisar el equivalente en `legacy/frontend-next/` para replicar el comportamiento real en vez de inventarlo. `legacy/` es de solo lectura.
3. Si la pieza ya está en el design system, leerla con `DesignSync` (`get_file`) en vez de aproximarla visualmente. Si el MCP `shadcn-ui` está disponible, consultar el componente real antes de usarlo, para no inventar props.
4. Ubicarla con el árbol de decisión de `arquitectura-frontend` §3.1.1: feature, capa transversal de la raíz, o `src/routes/` si es pantalla.
5. Crear o reusar el hook de TanStack Query en `features/<dominio>/api/`, con su query key de la fábrica y su `staleTime` de `config/query.ts`.
6. Construir la UI sobre las primitivas de `components/ui` antes de crear una nueva. Todo texto por `t()`, con la clave en `es` **y** en `en`.
7. Cubrir los **cuatro estados**: cargando, vacío, error y sin permiso.
8. **Si es página**: va en `src/routes/`, exporta `Component` además de su nombre, se registra en `src/routes/router.tsx` con `lazy` bajo el layout que corresponda, y **tiene al menos una puerta**: un enlace desde otra pantalla, construido con un builder de `config/paths.ts`. Una pantalla alcanzable solo escribiendo la URL es un huérfano (F4.1). Si es un destino nuevo de una notificación, se agrega a `notificationTarget.ts` apuntando a la pestaña que contiene lo que se anuncia.
9. JSDoc en inglés en cada `export`, con los `props` documentados campo por campo.
10. Test con Vitest + React Testing Library si tiene lógica o comportamiento condicional.
11. Si es un flujo crítico (login, crear mesa, postularse, subir archivo), agregarlo al spec de Playwright en `e2e/`.
