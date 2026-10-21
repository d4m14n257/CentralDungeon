# 2.1 Patrón: paquete por feature, capas adentro

> Parte de la skill `arquitectura` (ADR-01 §5). Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y las ADRs.


El árbol se organiza **por dominio de negocio primero, por capa técnica después**. Un `com.centraldungeon.controller` con 9 controllers de dominios distintos obliga a saltar entre 6 carpetas para tocar una sola funcionalidad; con paquete por feature, agregar o borrar una feature es agregar o borrar una carpeta.

Dentro de cada feature se respeta la arquitectura en capas clásica: `controller → service → repository`.

```
backend/src/main/java/com/centraldungeon/
├── CentralDungeonApplication.java
│
├── common/                              transversal, sin lógica de negocio propia
│   ├── config/                          @ConfigurationProperties (records) + @Configuration
│   │   ├── CorsConfig.java  DiscordProperties.java  JwtProperties.java  StorageProperties.java
│   │   ├── MapperConfig.java            config compartida de MapStruct
│   │   └── SchedulingConfig.java        habilita los jobs (@Scheduled)
│   ├── security/
│   │   ├── SecurityConfig.java          filter chain: permitAll de lo público y nada más —
│   │   │                                la autorización es @PreAuthorize en cada método (ADR-05 §4)
│   │   ├── JwtService.java  JwtAuthenticationFilter.java  InvalidJwtException.java  TokenType.java
│   │   ├── DiscordOAuth2UserService.java  valida membresía al guild + alta de usuario (ADR-05 §1)
│   │   ├── OAuth2LoginSuccessHandler.java  OAuth2LoginFailureHandler.java
│   │   ├── RefreshCookieFactory.java    la cookie httpOnly del refresh (ADR-05 §2)
│   │   ├── RestAuthenticationEntryPoint.java  401 como ProblemDetail
│   │   └── CurrentUser.java             @AuthenticationPrincipal tipado
│   ├── exception/
│   │   ├── ApiException.java            base (sealed): HttpStatus + código de error (ADR-23 §3)
│   │   ├── NotFoundException.java  ConflictException.java  ForbiddenActionException.java
│   │   ├── InvalidRequestException.java  UnauthorizedException.java
│   │   └── GlobalExceptionHandler.java  @RestControllerAdvice → RFC 7807 ProblemDetail
│   ├── storage/
│   │   ├── StorageService.java          interfaz (ADR-13 §6)
│   │   └── LocalDiskStorageService.java implementación por defecto
│   ├── search/                          el lenguaje de los buscadores (ADR-22 §1), sin dominio
│   │   ├── SearchQueryParser.java       `/campo valor or /campo valor` → SearchQuery
│   │   └── SearchQuery.java  SearchTerm.java  SearchConnector.java
│   │                                    ⚠ su espejo del frontend es lib/searchQuery.ts:
│   │                                      los dos cubren los mismos casos con tests
│   ├── text/
│   │   └── RichTextSanitizer.java       lista blanca del texto enriquecido (ADR-03 §8, ADR-05 §6)
│   └── model/
│       ├── BaseEntity.java              @MappedSuperclass: id String, created_at, updated_at
│       ├── IdGenerator.java             UUID v7 como String (ADR-03 §4)
│       └── PageResponse.java            envoltorio de paginación de la API
│
├── settings/                            una feature entera, como ejemplo de la forma
│   ├── SettingsController.java          lectura de los límites que ve el cliente
│   ├── AdminSettingsController.java     el Admin* de la misma feature: otra superficie, otro @PreAuthorize
│   ├── SettingsService.java             transacción y reglas (ADR-17 §3)
│   ├── SystemSettingRepository.java  SystemSettingChangeRepository.java
│   ├── SystemSetting.java  SystemSettingChange.java          @Entity
│   ├── SettingKey.java  SettingCategory.java  SettingValueType.java   enums
│   └── dto/
│       ├── SystemSettingResponse.java  SystemSettingChangeResponse.java  ClientLimitsResponse.java
│       └── UpdateSettingRequest.java
│
├── users/                               users, roles, users_roles, user_role_changes, user_status_changes
├── tables/                              game_tables, masters, table_schedules, table_sessions,
│                                        session_attendance, table_status_changes, table_types
├── registrations/                       table_registrations, registration_rejections, registration_status_changes
├── catalogs/                            systems, tags, platforms + tablas puente (AbstractCatalogService, §2.4)
├── files/                               files, file_categories, table_files, registration_files
├── tasks/                               table_tasks, task_submissions, submission_files
├── approvals/                           approval_requests (ADR-16 §1, §2)
├── adminqueue/                          la bandeja compartida, sin tabla propia (ADR-16 §5)
├── dashboard/                           el dashboard del master, solo lectura (ADR-20 §4)
├── profiles/                            perfiles públicos y su visibilidad (ADR-10 §1, §2)
├── notifications/                       notifications
├── auth/                                login, refresh, logout; y los dobles de prueba
│                                        (Test*Controller), solo con el perfil `test` (ADR-24 §1, §2)
└── health/                              /health
```

Los `comments/` y `feedback/` de F5 y el `audit/` de F6 todavía no existen: se crean como features cuando se construyan (§4.5 marca qué service les toca).

`src/main/resources/`:

```
application.yml              config base
application-dev.yml          perfil local
application-test.yml         perfil de tests (y de Playwright, con dev)
application-prod.yml         producción: sin defaults, todo por variable (§8.2)
db/migration/
├── V1__baseline.sql         schema completo (§4.4)
├── V2__seed.sql             roles + table_types iniciales (§4.6)
└── V3…V14                   una por cambio; la tabla de §4.4 dice qué hizo cada una
```
