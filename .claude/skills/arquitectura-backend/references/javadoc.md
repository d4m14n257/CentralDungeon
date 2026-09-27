# 2.8 Javadoc: la API pública documentada en el código

> Parte de la skill `arquitectura-backend`. Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


El proyecto se entrega para que el cliente lo use y lo siga mejorando. Eso pone la documentación **en el código**, no solo en `docs/`: quien abra una clase tiene que entender qué hace sin reconstruirlo leyendo su cuerpo.

**Todo lo `public` y `protected` lleva su Javadoc, sin excepciones**: clases, interfaces, enums, `record`, métodos, constructores y campos de entidad. **Getters y setters incluidos.** No hay umbral de trivialidad — un criterio de "solo lo que no se explica solo" es una discusión por método, y lo que produce es cobertura despareja.

- Cada componente de un `record` lleva su `@param`. **Todos**, incluidos `id` y `name`.
- Todo método con retorno lleva `@return`; el que lanza una excepción de negocio, su `@throws`.
- El bloque dice **qué hace y por qué existe**, nunca cómo está implementado: el cuerpo ya cuenta el cómo, y un comentario que lo repite se desincroniza en el primer refactor.
- Cuando una decisión de `decisiones.md` explica la forma de algo, se cita con su `#n`. Es lo que hace que el código se pueda mantener sin la conversación que lo produjo.
- **En inglés**, como el resto del código (`CLAUDE.md`, *Idioma*). El Javadoc es código.
- Se actualiza **en el mismo cambio** que la firma. Un `@param` que ya no existe es peor que no tener bloque.
