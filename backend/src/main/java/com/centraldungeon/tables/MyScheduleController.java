package com.centraldungeon.tables;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.tables.dto.WeeklyCommitmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader's own week: everything they run and everything they play at, in one answer (#227).
 *
 * <p>It sits outside the contexts on purpose. A person's week is not a master's week or a player's
 * week - it is theirs, and the whole reason the screen exists is that the two halves collide with
 * each other. Splitting it in two would hide exactly the overlap it is there to show.
 *
 * <p>No class-level {@code @RequestMapping}: the path names the reader, not a table, which is the
 * same reason {@code TableSessionController} declares its own.
 */
@RestController
public class MyScheduleController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final ScheduleConflictService scheduleConflictService;

    /**
     * @param scheduleConflictService the service that owns what a week is committed to
     */
    public MyScheduleController(ScheduleConflictService scheduleConflictService) {
        this.scheduleConflictService = scheduleConflictService;
    }

    /**
     * The reader's weekly commitments, as blocks in UTC minutes from Monday 00:00.
     *
     * <p>A list and not a page: a week has as many blocks as it has, bounded by how many tables one
     * person can be in, and it is read as one picture — the same criterion as a table's calendar.
     *
     * <p>Needs no role. Running a table and playing at one are both read here, and demanding either
     * would hide half the answer from somebody who only has the other (#103, #135).
     *
     * @param currentUser the actor, from the token. There is deliberately no parameter for whose
     *                    week to read: somebody else's is not public (#44, #121)
     * @return 200 with every table that claims a slot, each with the stretches it occupies. A table
     *         with no agenda yet comes back with no blocks rather than being left out
     */
    @GetMapping("/api/v1/users/me/schedule")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "The signed-in person's weekly commitments, as a master and as a player")
    public List<WeeklyCommitmentResponse> mySchedule(@AuthenticationPrincipal CurrentUser currentUser) {
        return scheduleConflictService.weekOf(currentUser.userId());
    }
}
