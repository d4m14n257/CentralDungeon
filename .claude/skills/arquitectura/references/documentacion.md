# 6. Documentación del código

> Parte de la skill `arquitectura` (#274). Junta el Javadoc (el viejo `arquitectura-backend` §2.8) y su contraparte JSDoc (que estaba en `arquitectura-frontend` §3.3), que son una misma regla aplicada a dos lenguajes. La regla en sí es la dura 19 de `CLAUDE.md`; acá está el cómo.

El proyecto se entrega para que el cliente lo use y lo siga mejorando. Eso pone la documentación **en el código**, no solo en `docs/`: quien abra una clase tiene que entender qué hace sin reconstruirlo leyendo su cuerpo.

## Lo que vale para los dos lados

- **Sin umbral de trivialidad.** Un criterio de «solo lo que no se explica solo» termina en una discusión por cada método, y lo que produce es cobertura despareja.
- **El bloque dice qué hace y por qué existe**, nunca cómo está implementado. El cuerpo ya cuenta el cómo, y un comentario que lo repite se desincroniza en el primer refactor.
- **Cita la decisión.** Cuando una decisión de `docs/decisiones.md` explica la forma de algo, se cita con su `#n`, y una regla de esta skill con su `§`. Es lo que permite mantener el código sin la conversación que lo produjo. Nunca se cita un documento por número de línea: la primera edición rompe la cita en silencio.
- **En inglés**, como el resto del código (`CLAUDE.md`, *Idioma*). El Javadoc y el JSDoc son código.
- **Se actualiza en el mismo cambio que la firma.** Un `@param` que ya no existe, o un bloque que describe una guardia que no está, es peor que no tener bloque. Una modificación que deja el bloque desactualizado es un bug.

## 6.1 Javadoc (backend)

**Todo lo `public` y `protected` lleva su Javadoc, sin excepciones**: clases, interfaces, enums, `record`, métodos, constructores y campos de entidad. **Getters y setters incluidos.**

- Cada componente de un `record` lleva su `@param`. **Todos**, incluidos `id` y `name`.
- Todo método con retorno lleva `@return`, y el que lanza una excepción de negocio, su `@throws`.
- Una clase de test documenta qué regla prueba y qué decisión la pide (§5.1).
- **Una migración Flyway aplicada no se toca, ni siquiera en sus comentarios.** El checksum cubre el archivo entero (§4.4). Una cita vieja en el comentario de una migración se deja como está.

## 6.2 JSDoc (frontend)

**Todo `export` lleva su bloque**: componentes, hooks, funciones, tipos, interfaces, constantes y esquemas zod.

- Los `props` de un componente se documentan **campo por campo en su `interface` o `type`**, y además con `@param props.x` en el bloque del componente.
- Un hook dice qué pregunta contesta, qué hace mientras la respuesta viaja, y qué `staleTime` usa y por qué (§3.3).
- **La única excepción del proyecto es `components/ui/`**: lo genera el CLI de shadcn y no se edita a mano.
- Una clase de patrón de `styles/base.css` también lleva su comentario: qué papel cumple y dónde está en el catálogo (skill `diseno` §5.c).
