---
adr: 20
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0020: TypeScript en el frontend nuevo

## Decisión

**Se adopta** (corregido: ver nota), con `strict` y un tipo base por entidad del que se derivan las variantes con utility types (`Pick`, `Omit`, `Partial`…)

## Por qué

**Corrección de agosto 2026**: la justificación original decía que el cambio Next.js→React era "de framework, no de lenguaje". Es falso — `legacy/frontend-next/` es **JavaScript**, con `jsconfig.json` y archivos `.js`/`.jsx`. En el frontend TypeScript no se conserva, **se introduce**; lo que se conservaba era el TypeScript del backend Node. La decisión no cambia, sí su motivo: el valor de TS está en que un cambio de contrato del backend se detecte compilando. Declarar a mano un tipo por cada variante (crear, actualizar, listar, detalle) es tener N copias del mismo modelo que se desincronizan en silencio — el compilador no avisa porque las copias siguen siendo válidas por separado. Derivando, el tipo base es el único punto de verdad. Detalle en skill `arquitectura` §3.2.
