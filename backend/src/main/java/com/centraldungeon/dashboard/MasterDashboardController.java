package com.centraldungeon.dashboard;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.dashboard.dto.MasterDashboardResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The master's work tray, {@code /master} (#136). */
@RestController
@RequestMapping("/api/v1/master")
public class MasterDashboardController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final MasterDashboardService masterDashboardService;

    /**
     * @param masterDashboardService builds the tray for one person
     */
    public MasterDashboardController(MasterDashboardService masterDashboardService) {
        this.masterDashboardService = masterDashboardService;
    }

    /**
     * Everything waiting for the caller's answer, across every table they run.
     *
     * <p>{@code isAuthenticated()} and no role, deliberately: running a table is a row in
     * {@code masters}, not the {@code Master} role (#135), so a role check here would hide the
     * screen from a co-master who legitimately has it. There is nothing to authorize beyond that -
     * the answer is keyed on the token's own actor and can never describe somebody else (#121).
     *
     * @param currentUser the actor, from the token
     * @return 200 with their work items, longest wait first. An empty list is a success: it means
     *         every table is up to date
     */
    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public MasterDashboardResponse getDashboard(@AuthenticationPrincipal CurrentUser currentUser) {
        return masterDashboardService.forMaster(currentUser.userId());
    }
}
