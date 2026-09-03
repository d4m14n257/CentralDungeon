# Diagramas

**Solo se versionan los `.mmd`.** Son la fuente de verdad y se leen bien en texto plano; los PNG se borraron por ser artefactos regenerables (2,6 MB de binario que no se puede diffear). Para verlos renderizados, el comando está al final.

Los diagramas del **modelo heredado** (`00`–`04`) ya cumplieron su función y se eliminaron. Lo que salió de revisarlos está en `../decisiones.md`.

## Modelo objetivo (ER)

Reflejan el schema de `../modelo-datos.md`, resultado de las decisiones #1–#95. Sin columnas de auditoría repetitivas: el DDL completo está en el documento.

| Diagrama | Qué cubre |
|---|---|
| [`11-objetivo-identidad.mmd`](11-objetivo-identidad.mmd) | `users`, `roles`, `users_roles`, `approval_requests`, `notifications`, `audit_logs`. Los cuatro roles y el mecanismo único de aprobaciones. |
| [`12-objetivo-mesa.mmd`](12-objetivo-mesa.mmd) | `game_tables`, `table_types`, `masters`, `table_schedules`, `table_sessions`, `session_attendance`, `table_status_changes`. |
| [`13-objetivo-ingreso.mmd`](13-objetivo-ingreso.mmd) | `table_registrations` sin `UNIQUE`, `registration_rejections`, `registration_files`. |
| [`14-objetivo-peticiones-archivos.mmd`](14-objetivo-peticiones-archivos.mmd) | `table_tasks`, `task_submissions`, `submission_files`, `files` rediseñada y `table_files`. |
| [`15-objetivo-catalogos.mmd`](15-objetivo-catalogos.mmd) | `systems`, `tags`, `platforms` con `canonical_id`, sus tres puentes, y `table_types` sin sinónimos. |
| [`16-objetivo-comentarios.mmd`](16-objetivo-comentarios.mmd) | `comment_drafts` (con autor) → `comments` (anónima), más `comment_quotas`, `system_feedback` y `feedback_quotas`. **Se ve dónde se corta el anonimato.** |

## Flujos

| Diagrama | Qué cubre |
|---|---|
| [`17-notificaciones.mmd`](17-notificaciones.mmd) | Motor de notificaciones: la personal como fila en `notifications`, la bandeja de admins como **vista** sobre el trabajo pendiente, el ciclo de reserva y el mensaje como señal de invalidación. |
| [`19-choque-horarios.mmd`](19-choque-horarios.mmd) | Las cuatro reglas de #178: qué cuenta como choque —intervalo semiabierto en UTC, con envoltura semanal— y qué pasa en cada uno de los tres momentos (el master se compromete, el jugador se postula, el master acepta). **Dónde se bloquea y dónde solo se avisa.** |
| [`18-navegacion.mmd`](18-navegacion.mmd) | Navegación real entre pantallas — de qué router.tsx/Link/navigate sale cada flecha, no el sitemap ideal. **Vivo, no un cierre de etapa**: se actualiza cuando una etapa nueva conecta o desconecta pantallas, para ver crecer las conexiones entre vista y vista de una etapa a la otra. Nació del artifact de revisión de E1 (`pruebas-e1.md`), rescatado acá al cerrar esa etapa. |

## Ciclos de vida

| Diagrama | Qué cubre |
|---|---|
| [`05-ciclo-mesa.mmd`](05-ciclo-mesa.mmd) | Máquina de estados de la mesa, con `ChangesRequested` y `PauseRequested`. |
| [`06-ciclo-solicitud.mmd`](06-ciclo-solicitud.mmd) | Ciclo de una postulación: N por mesa, una activa, veto acotado. |
| [`07-ciclo-comentario.mmd`](07-ciclo-comentario.mmd) | Borrador → confirmado al cerrar la mesa → moderación → karma. |

## Regenerar

```bash
cd docs/diagramas
PUPPETEER_EXECUTABLE_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  npx -y @mermaid-js/mermaid-cli -i 12-objetivo-mesa.mmd -o 12-objetivo-mesa.mmd -b white -s 3
```

`PUPPETEER_EXECUTABLE_PATH` evita que mermaid-cli descargue su propio Chromium.
