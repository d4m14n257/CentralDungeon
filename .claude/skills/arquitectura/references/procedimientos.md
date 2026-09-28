# 7. Procedimientos

> Parte de la skill `arquitectura` (#274). Eran tres skills separadas —`nuevo-endpoint-java`, `nuevo-componente-react` y `er-diagram-sync`— y ahora son los pasos de esta, porque ninguno tiene sentido sin las reglas que aplica. **Las reglas no están acá**: son las reglas fijas del `SKILL.md`, y se aplican todas. Esto es solo el orden.

## 7.1 Nuevo endpoint (backend)

1. **Confirmar qué tablas toca**, contra el DDL (§4.4, más la tabla de migraciones posteriores) y la regla de negocio que corresponde (§4.5). Si el endpoint toca roles, bloqueo, pedidos, veto o ajustes, también `references/modelo-datos/roles-y-alcance.md`.
2. **Si el schema cambia**: §7.3.
3. Crear o ajustar la `@Entity` en el paquete de la feature, con `LAZY` por defecto y los enums con `@Enumerated(EnumType.STRING)`.
4. Crear el `JpaRepository`, con `@Query` de parámetros nombrados y **el actor en el `WHERE`** cuando el recurso tiene dueño (§2.6).
5. Crear los DTO en `dto/`: request y response separados, `record`, validación Jakarta en el de entrada, y Javadoc con un `@param` por componente (§2.3, §6.1).
6. Crear el mapper MapStruct si la traducción no es trivial.
7. Implementar el método de negocio en el service:
   - la transacción;
   - la verificación de pertenencia **antes** de tocar nada;
   - para cada negativa, la excepción de `common/exception` con su código (#197).
8. Crear el método del controller:
   - ruta bajo `/api/v1`, con el recurso en plural y en kebab-case;
   - status explícito: `201` + `Location` al crear, `204` sin cuerpo;
   - `@PreAuthorize` en el método, enumerando sus roles (§2.5, §2.6).
9. **Test del service** antes de dar el endpoint por terminado, y su IT si la regla depende del motor real (§5.1).
10. Si el código de error es nuevo, **su clave va en `es` y en `en`** del frontend en el mismo commit (#197, #198).
11. Si lo va a llamar el frontend, su tipo en `features/<dominio>/types.ts` tiene que ser el espejo exacto del `record` de respuesta (§3.2).

## 7.2 Nuevo componente o pantalla (frontend)

1. **Leer la skill `diseno`**, en especial:
   - el inventario (§5): si lo que se va a escribir ya existe —un badge de estado, un diálogo con motivo obligatorio, un selector—, se reusa (#261);
   - el catálogo de patrones (§5.c): la cabecera, el título, las acciones de fila y las listas **ya tienen nombre**, y una pantalla no los vuelve a escribir (#273).
2. Si reconstruye algo que existía, revisar el equivalente en `legacy/frontend-next/` para replicar el comportamiento real en vez de inventarlo. `legacy/` es de solo lectura.
3. Si la pieza ya está en el design system, leerla con `DesignSync` (`get_file`) en vez de aproximarla a ojo. Si el MCP `shadcn-ui` está disponible, consultar el componente real antes de usarlo, para no inventar props.
4. Ubicarla con el árbol de decisión de §3.1.1: en una feature, en la capa transversal de la raíz, o en `src/routes/` si es una pantalla.
5. Crear o reusar el hook de TanStack Query en `features/<dominio>/api/`, con su query key de la fábrica y su `staleTime` de `config/query.ts` (§3.3).
6. Construir la UI sobre las primitivas de `components/ui`, los componentes de `components/` y las clases de patrón. Todo texto pasa por `t()`, con la clave en `es` **y** en `en`.
7. Cubrir los **cuatro estados**: cargando, vacío, error y sin permiso (skill `diseno` §5).
8. **Si es una pantalla**:
   - va en `src/routes/` y exporta `Component` además de su nombre;
   - se registra en `src/routes/router.tsx` con `lazy`, bajo el layout de su contexto, que ya la cierra a quien no tiene el contexto (#269);
   - **tiene al menos una puerta**: un enlace desde otra pantalla, construido con un builder de `config/paths.ts`. Una pantalla que solo se alcanza escribiendo la URL es un huérfano (F4.1);
   - si es el destino de una notificación nueva, se agrega a `notificationTarget.ts`, apuntando a la pestaña que contiene lo que la notificación anuncia.
9. JSDoc en inglés en cada `export`, con los `props` documentados campo por campo (§6.2).
10. Test con Vitest + React Testing Library si tiene lógica o comportamiento condicional (§5.2). Si es un flujo crítico, su spec de Playwright (§5.3).

## 7.3 Cambio de una `@Entity` (schema)

El proyecto viejo terminó con dos `database.sql` desactualizados en dos repos distintos, uno de ellos con errores de sintaxis que impedían ejecutarlo. El modelo de esta skill (§4) es la fuente de verdad del schema y no puede quedar atrás del código (regla dura 10).

**Cuándo aplica**: a cualquier cambio de una clase `@Entity`, sea tabla nueva, columna nueva, relación nueva o eliminada, cambio de tipo o valor nuevo en un enum.

1. **Migración Flyway nueva** en `backend/src/main/resources/db/migration/`, con el número siguiente al último que exista (hacer `ls` antes de elegirlo). Nunca se edita una migración aplicada (regla dura 9). El comentario SQL de cabecera dice **por qué**, con su `#n`, en inglés.
2. **Actualizar `references/modelo-datos/ddl.md`** con **una fila nueva** en la tabla de migraciones: qué cambió y por qué. El bloque DDL de ese archivo es el `V1__baseline.sql` literal y no se toca, igual que la migración.
3. **Actualizar el diagrama entidad-relación**: el bloque Mermaid de `docs/modelo-datos.md` §3 (los atributos de la entidad, y la línea de relación si cambió una FK) **y** el `.mmd` del subsistema en `docs/diagramas/11`–`16`.
4. **Respetar las convenciones** de §4.1:
   - tablas en `snake_case` y plural;
   - PK `id` `VARCHAR(64)`, generada en la aplicación;
   - enums como `VARCHAR(32)`, nunca el tipo `ENUM` de MySQL;
   - soft delete con `status` y `deleted_at`, más `created_at` y `updated_at`;
   - cascadas en el service, nunca `ON DELETE CASCADE`.
5. Si el cambio agrega o modifica una **regla de negocio**, anotarla en `references/modelo-datos/reglas-negocio.md` (§4.5) con el service donde vive, y escribir su test.
6. Si la tabla nueva la llena el e2e, agregarla al orden de borrado de `TestDataService` (#171, #172).
7. Si el cambio **contradice una decisión** ya registrada, actualizar `docs/decisiones.md` en el mismo commit, para que el registro no quede mintiendo.
8. Antes de dar el cambio por terminado, verificar que los bloques Mermaid siguen siendo válidos: nombres de entidad consistentes y sin comas sueltas.
