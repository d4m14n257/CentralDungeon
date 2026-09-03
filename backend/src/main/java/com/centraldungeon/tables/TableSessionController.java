package com.centraldungeon.tables;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.tables.dto.MySessionsResponse;
import com.centraldungeon.tables.dto.RecordAttendanceRequest;
import com.centraldungeon.tables.dto.TableSessionResponse;
import com.centraldungeon.tables.dto.UpdateSessionRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A table's sessions, from both ends: the master manages the calendar, the player reads their own.
 *
 * <p>No class-level {@code @RequestMapping}, same as {@code RegistrationController}: a table's
 * sessions hang off the table ({@code /game-tables/{id}/sessions}), while acting on one addresses
 * the session itself ({@code /sessions/{id}/hold}).
 *
 * <p>Every method is {@code isAuthenticated()} and none names a role. Who may touch a concrete
 * table's calendar is <b>pertenencia</b> - a row in {@code masters} - which a {@code @PreAuthorize}
 * cannot see, so the service checks it before reading anything (#17, #121, #135). The annotation is
 * still written out on each method and never inherited (regla dura 4, CVE-2025-41248).
 */
@RestController
public class TableSessionController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final TableSessionService tableSessionService;

    /**
     * @param tableSessionService the service that owns the calendar and its rules
     */
    public TableSessionController(TableSessionService tableSessionService) {
        this.tableSessionService = tableSessionService;
    }

    /**
     * The whole calendar as the people running the table see it.
     *
     * <p>A list and not a page: it is bounded by {@code total_sessions} and read as one calendar,
     * the same criterion as the table's status history.
     *
     * @param tableId     the table
     * @param currentUser the actor, from the token; the service checks they run the table
     * @return 200 with its sessions, first to last. 403 when the actor does not run it. While the
     *         table is paused the pending sessions are not in the list (#32, #33)
     */
    @GetMapping("/api/v1/game-tables/{tableId}/sessions")
    @PreAuthorize("isAuthenticated()")
    public List<TableSessionResponse> listForTable(@PathVariable String tableId, @AuthenticationPrincipal CurrentUser currentUser) {
        return tableSessionService.listForTable(tableId, currentUser.userId());
    }

    /**
     * {@code /my/tables/:id} - a player's own calendar and their own attendance.
     *
     * @param tableId     the table
     * @param currentUser the actor, from the token. Both halves of the answer are about them and
     *                    there is no parameter that could name anybody else (#121)
     * @return 200 with their sessions and the three numbers of #137. 403 when they do not play there
     */
    @GetMapping("/api/v1/game-tables/{tableId}/sessions/mine")
    @PreAuthorize("isAuthenticated()")
    public MySessionsResponse listMine(@PathVariable String tableId, @AuthenticationPrincipal CurrentUser currentUser) {
        return tableSessionService.listMine(tableId, currentUser.userId());
    }

    /**
     * A master correcting one session: its date, its notes, or both.
     *
     * @param sessionId   the session
     * @param request     the new date and notes
     * @param currentUser the actor, from the token
     * @return 200 with the session after the correction. 403 when the actor does not run the table,
     *         409 when the session was already played or called off
     */
    @PatchMapping("/api/v1/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public TableSessionResponse update(
            @PathVariable String sessionId,
            @Valid @RequestBody UpdateSessionRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return tableSessionService.update(sessionId, request, currentUser.userId());
    }

    /**
     * A master declaring a session was played. Separate from recording attendance on purpose (#195).
     *
     * @param sessionId   the session
     * @param currentUser the actor, from the token
     * @return 200 with the session, now held. 409 when it was already played or called off
     */
    @PostMapping("/api/v1/sessions/{sessionId}/hold")
    @PreAuthorize("isAuthenticated()")
    public TableSessionResponse hold(@PathVariable String sessionId, @AuthenticationPrincipal CurrentUser currentUser) {
        return tableSessionService.hold(sessionId, currentUser.userId());
    }

    /**
     * A master calling off one session. The table gets it back at the end (#194).
     *
     * @param sessionId   the session
     * @param currentUser the actor, from the token
     * @return 200 with the whole calendar: the cancellation and its replacement are one change, and
     *         answering with the list is what lets the screen re-render from the response
     */
    @PostMapping("/api/v1/sessions/{sessionId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public List<TableSessionResponse> cancel(@PathVariable String sessionId, @AuthenticationPrincipal CurrentUser currentUser) {
        return tableSessionService.cancel(sessionId, currentUser.userId());
    }

    /**
     * A master recording who was at a session (#36).
     *
     * <p>{@code PUT} because the roster travels as a whole: it is filled in as one list on screen,
     * and a half-saved roster would be a state the screen has to explain.
     *
     * @param sessionId   the session
     * @param request     one line per player being recorded
     * @param currentUser the actor, from the token
     * @return 200 with the session and its roster. 400 when a line names somebody who does not play
     *         at the table, 409 when the session was called off
     */
    @PutMapping("/api/v1/sessions/{sessionId}/attendance")
    @PreAuthorize("isAuthenticated()")
    public TableSessionResponse recordAttendance(
            @PathVariable String sessionId,
            @Valid @RequestBody RecordAttendanceRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return tableSessionService.recordAttendance(sessionId, request, currentUser.userId());
    }
}
