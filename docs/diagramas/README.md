# Diagramas

**Solo se versionan las fuentes.** Los `.mmd` se leen bien en texto plano; los PNG se borraron por ser artefactos regenerables (2,6 MB de binario que no se puede diffear). Desde F3 hay una segunda clase de fuente, los `.architecture.json` de **Archify**, que rinden a un HTML interactivo — misma regla: se versiona el JSON, nunca el HTML de ~800 KB ni las capturas que deja su verificación. Los dos comandos de regeneración están al final.

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

## Interactivos (Archify)

Fuente `.architecture.json`, salida HTML autocontenida con temas claro/oscuro, zoom, búsqueda y exportación. La salida está en `.gitignore`.

| Diagrama | Qué cubre |
|---|---|
| [`20-navegacion-f1-f3.architecture.json`](20-navegacion-f1-f3.architecture.json) | **Navegación real de la UI al cerrar F3.** Las cuatro regiones —público, Jugador, Master, Admin— y de qué sale cada flecha, leído del `router.tsx` y de cada `Link`/`NavLink`/`navigate` reales. Las tarjetas responden las tres preguntas que motivaron el diagrama: qué está conectado, **qué quedó flotando** y qué pantallas tienen una sola entrada. Fija el commit del que se leyó la evidencia, así que un diagrama viejo se delata solo. |

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

Para los interactivos, con la skill `archify` instalada en `~/.claude/skills/`:

```bash
ARCHIFY=~/.claude/skills/archify
node $ARCHIFY/bin/archify.mjs deliver architecture \
  docs/diagramas/20-navegacion-f1-f3.architecture.json \
  docs/diagramas/20-navegacion-f1-f3.html \
  --quality showcase --repo-root .
node $ARCHIFY/bin/archify.mjs visual-check docs/diagramas/20-navegacion-f1-f3.html --json
```

`--repo-root` es lo que hace que las citas `sources` de cada nodo se verifiquen contra los
archivos reales: si una ruta deja de existir, la entrega falla en vez de publicar un diagrama que
miente. `meta.repository.revision` deja fijado el commit del que se leyó esa evidencia.
