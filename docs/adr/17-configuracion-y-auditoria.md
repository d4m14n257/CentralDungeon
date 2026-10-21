---
adr: 17
titulo: Configuración del sistema y auditoría
estado: Vigente
actualizada: 2026-10-20
absorbe: [92, 141, 251, 263, 264]
---

# ADR-17: Configuración del sistema y auditoría

> Qué valores de la plataforma se cambian sin desplegar, cómo se publican al cliente, y qué queda registrado de cada cambio de datos. Las reglas de código y el DDL están en `arquitectura` §2 y §4. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 La auditoría registra cambios de datos, con el diff de las columnas que cambiaron

**Decisión.** `audit_logs` registra qué entidad se modificó, su id, quién, cuándo, y el antes y el después **solo de las columnas que cambiaron**, no la fila completa. La consulta es una vista exclusiva del `Owner`. **`comments` y `comment_drafts` no se auditan** (ADR-11 §5).

**Por qué.** No es analítica de uso —nadie registra clics— sino trazabilidad de **cambios de datos**, para reconstruir qué hizo cada persona. Guardar solo el diff mantiene la tabla manejable y cada fila legible. Los comentarios quedan fuera a propósito: auditarlos guardaría autor y contenido juntos y anularía el anonimato. Es un hueco deliberado, no un olvido.

*Antes: #92.*

## §2 Los cambios de una cuenta tienen su propio historial inmutable, con justificación

**Decisión.** `user_role_changes` y `user_status_changes`, con la forma de `table_status_changes`: fila inmutable —sin `updated_at`, sin `deleted_at`, sin setters—, id UUID v7 en `@PrePersist`, constructor con todo y **justificación obligatoria** (`NOT NULL` en la tabla, `@NotBlank` en el request). Se leen por `GET /api/v1/admin/users/{id}/history`, normalizadas en un `UserAdminChangeResponse` común. Los cambios de ajustes del sistema (§3) siguen la misma forma en `system_setting_changes`. `audit_logs` no las reemplaza: las complementa.

**Por qué.** Otorgar un rol o bloquear una cuenta no se aprueba, se hace, así que registrarlo en `approval_requests` (ADR-16 §1) mezclaría un acto con un pedido; y esperar a `audit_logs` dejaría esos cambios sin responsable durante fases enteras. La tabla propia es el precedente que el proyecto ya usa para un cambio de estado con justificación: `audit_logs` cubre lo transversal con diff de columnas, estas son el historial específico, con el motivo. Un par de columnas `updated_by`/`updated_at` que la próxima edición pisa no puede decir que algo cambió el martes y volvió el miércoles. El endpoint de lectura entra con las tablas: sin él serían escritura pura.

*Antes: #251.*

## §3 La configuración es un catálogo en el código y una tabla que solo guarda lo que alguien cambió

**Decisión.** El catálogo es el `enum` `SettingKey`, con valor por defecto, rango, categoría y si el cambio es retroactivo. `system_settings` es clave-valor y guarda **solo overrides**: sin fila para un ajuste que nadie tocó, sin seed; cambiar un default es editar el `enum`. Lo editan los admins en `/admin/settings` y cada cambio queda auditado (§2). Dos categorías, `Business` y `Limits`; no hay ajustes de texto. Hoy son cuatro: el timeout de reserva de la bandeja (15 min), el tope por archivo (2 MB), el cupo máximo de jugadores (12) y la ventana de visibilidad de perfiles (14 días, retroactiva, ADR-10 §2). Un ajuste entra cuando hay algo que lo lea: el karma inicial y su ventana de decaimiento (ADR-11 §9) y la ventana del feedback (ADR-11 §12) llegan con sus subsistemas. **Ningún secreto vive acá**: el HMAC de ADR-11 §12 y las credenciales siguen en el entorno.

**Por qué.** La comunidad va a querer mover estos valores sin un despliegue. Clave-valor porque sumar un ajuste no debe ser un `ALTER TABLE` (ADR-03 §5); la regla dura 3 no se rompe porque `SettingsService` expone accesores tipados y el endpoint devuelve su `record`. Sin seed, una base recién vaciada se comporta igual que un alta, y el `enum` es lo que deja a la pantalla mostrar «valor actual y por defecto». No hay textos porque el backend no escribe frases (ADR-23 §3): un ajuste de texto sería una frase en un solo idioma, la regla dura 18 rota por diseño. Un ajuste que nada lee es una perilla que no hace nada. Cambiar una ventana retroactiva altera quién ve a quién de inmediato, y la pantalla tiene que decirlo.

*Antes: #141, #263.*

## §4 Los límites que el cliente necesita los publica un endpoint propio

**Decisión.** `GET /settings/limits` devuelve `ClientLimitsResponse` —el tope por archivo y el timeout de reserva de la bandeja— a cualquiera con sesión, fuera de `/admin`. Lo lee `hooks/useClientLimits`, con los defaults de `config/limits.ts` mientras el pedido viaja. El servidor sigue aplicando el límite y contesta `FILE_TOO_LARGE` con el número real (ADR-23 §3). El cupo máximo no viaja.

**Por qué.** Si un admin mueve el tope sin desplegar, un espejo fijo en el frontend rechazaría archivos que el servidor acepta, y «la subida siguiente lo respeta sin reiniciar nada» sería falso. Va en `hooks/` y no en una feature porque `features/files` lo lee y `features/settings` lo edita, y una feature no importa otra (regla dura 16): es configuración de plataforma. El cupo máximo no viaja porque ninguna pantalla lo enuncia todavía, y un campo que nadie renderiza es un huérfano.

*Antes: #264.*
