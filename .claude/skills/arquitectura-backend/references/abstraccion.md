# 2.4 Interfaces y clases abstractas: qué se comparte y qué no

> Parte de la skill `arquitectura-backend`. Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


Hay tres formas de tratar código parecido entre features, y elegir mal cuesta caro en las dos direcciones — duplicar una regla de negocio genera bugs divergentes, y abstraer dos cosas que solo se parecían acopla dominios que después hay que separar a la fuerza.

**1. Interfaz — cuando hay, o va a haber, más de una implementación real.**

Es el caso de `StorageService`: hoy escribe en disco local, mañana puede escribir en S3, y el resto del backend no debería enterarse (`decisiones.md` #15). La interfaz vive en `common/<área>/`, la implementación al lado, y se inyecta siempre la interfaz.

Lo que **no** se hace: `UserServiceImpl implements UserService` con una sola implementación y sin intención de tener otra. No aporta nada, duplica la navegación entre archivos y no hace falta para testear — Mockito mockea clases concretas sin problema.

**2. Clase abstracta genérica — cuando la misma forma se repite idéntica en tres o más features.**

El caso real del proyecto son los catálogos. `systems`, `tags` y `platforms` tienen la misma estructura (id, nombre, descripción, `parent_id`) y el mismo CRUD. Escribir tres veces el mismo service es mantener tres veces el mismo bug.

```java
public abstract class AbstractCatalogService<E extends CatalogEntity, R extends CatalogResponse> {

    protected final JpaRepository<E, String> repository;
    protected final CatalogMapper<E, R> mapper;

    @Transactional(readOnly = true)
    public PageResponse<R> findAll(Pageable pageable) { /* igual para los tres */ }

    @Transactional
    public R create(CatalogRequest request) {
        requireNameIsUnique(request.name());
        return mapper.toResponse(repository.save(newEntity(request)));
    }

    /** Cada catálogo construye su propia entidad. */
    protected abstract E newEntity(CatalogRequest request);

    /** Gancho opcional: por defecto no valida nada extra. */
    protected void validateBeforeDelete(E entity) { }
}
```

Condiciones para que esto sea legítimo:

- **Se extrae después de ver la repetición, no antes.** Primero se escribe `SystemService` completo; cuando `TagService` sale idéntico y `PlatformService` también, ahí se abstrae. Una base genérica escrita antes de la segunda implementación siempre termina teniendo la forma equivocada.
- Lo abstracto es el CRUD mecánico. Lo que varía se expone como método abstracto o gancho `protected`, **nunca** como un `if (this instanceof TagService)`.
- **Un solo nivel de herencia.** Una clase abstracta que extiende otra clase abstracta deja de poder leerse.
- Si solo la usa una feature, vive en esa feature. A `common/` sube únicamente lo que usan features distintas.

**3. Nada — cuando el parecido es superficial.**

`GameTableService` y `RegistrationService` comparten un `findById` que lanza `NotFoundException`, y ahí termina el parecido: sus reglas de negocio no tienen relación. Una base común entre ellos acopla dos dominios que van a divergir en el primer requerimiento nuevo. Repetir tres líneas es más barato que desacoplarlos después.

> Criterio: **se abstrae lo que es igual por definición** (la forma de un catálogo), **no lo que hoy es parecido por casualidad** (dos services que ambos leen por id).

**Controllers**: mismo criterio, con una salvedad. Heredar un controller esconde el mapeo HTTP, así que aunque la lógica venga de una base genérica, **la ruta y las anotaciones de autorización se declaran en la subclase concreta**. Leer `SystemController.java` tiene que seguir diciendo qué expone y quién puede llamarlo.

**Lo que no se abstrae nunca es la seguridad.** Un `@PreAuthorize` heredado de una base genérica hace que el permiso de un endpoint sea invisible en el archivo que lo declara. Cada controller concreto declara su propia autorización, aunque sea repetitivo.

**Interfaces selladas** (`sealed interface`, `sealed class`): se usan para modelar variantes cerradas del dominio — `ApiException` ya lo hace — y para poder usar `switch` con pattern matching exhaustivo. No son un mecanismo para compartir implementación.
