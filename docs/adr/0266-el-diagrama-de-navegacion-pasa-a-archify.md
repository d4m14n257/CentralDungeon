---
adr: 266
estado: Aceptada
fecha: 2026-09-21
modificada-por: [267]
modifica: [162]
---

# ADR-0266: El diagrama de navegación pasa a Archify y 18-navegacion.mmd se borra

> Reemplaza #162

## Decisión

Nuevo `docs/diagramas/20-navegacion-f1-f3.architecture.json`, que rinde a un HTML interactivo con la skill externa `archify`. Se versiona el JSON y **no** el HTML ni las capturas, la misma regla que ya regía para los `.mmd` y sus PNG. El `.mmd` anterior se elimina en vez de actualizarse

## Por qué

#162 lo declaró «vivo, se actualiza a mano cuando una etapa conecta o desconecta pantallas», y eso es exactamente lo que no pasó: al cerrar F3 seguía con las rutas de E1 —`/tables/:id`, `/my/applications`— y marcaba como «no construido» siete pantallas que existen desde hace dos fases. **Un diagrama que miente es peor que ninguno**, porque se lee como si fuera cierto. Lo que falla no es la disciplina sino el mecanismo: nada podía detectar que se había quedado atrás. El formato nuevo sí puede: `meta.repository.revision` fija el commit del que se leyó la evidencia y cada nodo cita sus archivos en `sources`, que `--repo-root` verifica al entregar — si una ruta deja de existir la entrega **falla** en vez de publicar. Se borra en vez de mantener los dos porque dos diagramas de lo mismo, donde uno está desactualizado, es el problema de #162 multiplicado por dos; el registro de lo que decía queda en git. La skill es externa y global a propósito, fuera de `.claude/skills/`, que `entorno-local` §6 reserva para las propias
