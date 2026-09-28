# 2.1 Patrón: paquete por feature, capas adentro

> Parte de la skill `arquitectura` (#274). Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


El árbol se organiza **por dominio de negocio primero, por capa técnica después**. Un `com.centraldungeon.controller` con 9 controllers de dominios distintos obliga a saltar entre 6 carpetas para tocar una sola funcionalidad; con paquete por feature, agregar o borrar una feature es agregar o borrar una carpeta.

Dentro de cada feature se respeta la arquitectura en capas clásica: `controller → service → repository`.

```
backend/src/main/java/com/centraldungeon/
├── CentralDungeonApplication.java
│
├── common/                              transversal, sin lógica de negocio propia
│   ├── config/                          @ConfigurationProperties (records) + @Configuration
│   │   ├── DiscordProperties.java
│   │   ├── JwtProperties.java
│   │   ├── StorageProperties.java
│   │   ├── CorsConfig.java
│   │   └── JacksonConfig.java
│   ├── security/
│   │   ├── SecurityConfig.java          filter chain, stateless, matchers por rol
│   │   ├── JwtService.java              emisión y validación
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── DiscordOAuth2UserService.java  valida membresía al guild + alta de usuario
│   │   └── CurrentUser.java             @AuthenticationPrincipal tipado
│   ├── exception/
│   │   ├── ApiException.java            base (sealed) — lleva HttpStatus + código de error
│   │   ├── NotFoundException.java
│   │   ├── ConflictException.java
│   │   ├── ForbiddenActionException.java
│   │   └── GlobalExceptionHandler.java  @RestControllerAdvice → RFC 7807 ProblemDetail
│   ├── audit/
│   │   ├── AuditLog.java                @Entity
│   │   ├── AuditLogRepository.java
│   │   └── AuditService.java            lo invocan los services, nunca los controllers
│   │                                    ⚠ NUNCA audita `comments`: guardaría autor + contenido
│   │                                      y rompería el anonimato (decisiones.md #43)
│   ├── storage/
│   │   ├── StorageService.java          interfaz
│   │   └── LocalDiskStorageService.java implementación por defecto
│   ├── search/                          el lenguaje de los buscadores (#164), sin dominio
│   │   ├── SearchQuery.java             la consulta parseada: lista de SearchTerm
│   │   ├── SearchTerm.java              campo + valor + conector con el término anterior
│   │   ├── SearchConnector.java         AND / OR — izquierda a derecha, sin precedencia
│   │   └── SearchQueryParser.java       `/campo valor or /campo valor` → SearchQuery
│   │                                    ⚠ su espejo del frontend es lib/searchQuery.ts:
│   │                                      los dos cubren los mismos casos con tests
│   └── model/
│       ├── BaseEntity.java              @MappedSuperclass: id String, created_at, updated_at
│       ├── IdGenerator.java             UUID v7 como String
│       └── PageResponse.java            envoltorio de paginación de la API
│
├── users/                               users, roles, users_roles
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── User.java                        @Entity
│   ├── Role.java
│   ├── UserRole.java
│   ├── UserStatus.java                  enum
│   ├── UserMapper.java                  @Mapper(componentModel = "spring")
│   └── dto/
│       ├── UserResponse.java            record
│       ├── UserDetailResponse.java
│       └── UpdateUserRequest.java
│
├── tables/                              game_tables, masters, table_schedules, table_types
│   ├── GameTableController.java
│   ├── TableTypeController.java
│   ├── GameTableService.java
│   ├── TableScheduleService.java
│   ├── MasterService.java
│   ├── GameTableRepository.java
│   ├── ...Entity/enum/mapper...
│   └── dto/
│
├── registrations/                       table_registrations, registration_rejections
├── catalogs/                            systems, tags, platforms + tablas puente
├── files/                               files, table_files, registration_files
├── comments/                            comments + ajuste de karma
├── requests/                            requests (rol / master)
└── notifications/                       notifications
```

`src/main/resources/`:

```
application.yml              config base
application-dev.yml          perfil local
application-test.yml         perfil de tests
db/migration/
├── V1__baseline.sql         schema completo (ver skill `arquitectura` §4.4)
└── V2__seed.sql             roles + table_types iniciales
```
