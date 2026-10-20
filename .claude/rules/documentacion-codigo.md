---
paths:
  - "backend/src/**/*.java"
  - "frontend/src/**/*.ts"
  - "frontend/src/**/*.tsx"
  - "frontend/e2e/**/*.ts"
---

# Documentación del código — regla dura 19

> Se carga al tocar código. El detalle, con ejemplos, está en la skill `arquitectura` §6.

El cliente recibe este proyecto para usarlo y para seguir mejorándolo. Eso exige que **la API pública del código esté documentada en el código**, no solo en `docs/`: quien abra una clase tiene que entender qué hace sin reconstruirlo leyendo su cuerpo.

**Regla:** toda clase, interfaz, enum, `record`, método, constructor, campo, función, hook, componente y tipo exportado lleva su bloque. **Incluidos los getters y setters.** No hay umbral de trivialidad ni lista de excepciones: si es público, se documenta.

| | Formato | Alcance |
|---|---|---|
| Backend | **Javadoc** (`/** … */`) | Todo lo `public` y `protected`, más los campos de entidad. Cada componente de un `record` lleva su `@param`, todos, incluso `id` y `name`. Métodos con retorno llevan `@return`; los que lanzan una excepción de negocio, su `@throws` |
| Frontend | **JSDoc** (`/** … */`) | Todo `export`: componentes, hooks, funciones, tipos, interfaces, constantes y esquemas zod. Los `props` de un componente se documentan en su `interface` o `type`, campo por campo |

**Qué escribir.** El bloque dice **qué hace y por qué existe**, nunca cómo está implementado: el cuerpo ya cuenta el cómo, y un comentario que lo repite se desincroniza en el primer refactor. Cuando una decisión explica la forma de algo, se cita con su `#n` (la ADR de `docs/adr/`): es lo que convierte el código en algo que se puede mantener sin la conversación que lo produjo.

**En inglés, como todo el código.** Un archivo de código no tiene una sola palabra en español, y el Javadoc y el JSDoc son código.

**Se mantiene al mismo tiempo que el código.** Cambiar una firma, un `@param` o el significado de un valor de retorno sin tocar el bloque deja documentación que miente, que es peor que no tener ninguna. Código nuevo sin documentar no está terminado.

`components/ui/` es lo único fuera de esto: es código generado por el CLI de shadcn.
