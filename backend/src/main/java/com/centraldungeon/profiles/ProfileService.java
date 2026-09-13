package com.centraldungeon.profiles;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.profiles.dto.ProfileResponse;
import com.centraldungeon.tables.TableSessionService;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Somebody's profile, assembled once {@link ProfileVisibilityService} has decided the actor may read
 * it (modelo-datos.md §5).
 *
 * <p><b>Not in {@code users/}.</b> A profile has to read who runs a table, who applied to one and who
 * plays at it, and {@code users/} already sits underneath {@code tables/} the other way around -
 * {@code MasterService} depends on {@code UserService}. Putting the visibility rules inside
 * {@code users/} would close that into a cycle. This package depends on {@code users}, {@code tables}
 * and {@code registrations} instead, the exact precedent of decision #219, which pulled
 * {@code MasterDashboardService} out into {@code dashboard/} rather than let {@code tables/} depend
 * on {@code tasks/}.
 */
@Service
public class ProfileService {

    /** Loads the person a profile describes. */
    private final UserService userService;

    /** The global roles a profile lists. */
    private final UserRoleRepository userRoleRepository;

    /** The aggregate attendance a profile shows, across every table (#137). */
    private final TableSessionService tableSessionService;

    /** The one gate every read goes through - the five rules of §5, in one place. */
    private final ProfileVisibilityService visibilityService;

    /**
     * @param userService          loads the person a profile describes
     * @param userRoleRepository   the roles a profile lists
     * @param tableSessionService  the aggregate attendance a profile shows
     * @param visibilityService    the visibility rules, all five, in one place
     */
    public ProfileService(
            UserService userService,
            UserRoleRepository userRoleRepository,
            TableSessionService tableSessionService,
            ProfileVisibilityService visibilityService) {
        this.userService = userService;
        this.userRoleRepository = userRoleRepository;
        this.tableSessionService = tableSessionService;
        this.visibilityService = visibilityService;
    }

    /**
     * Somebody's profile, as the actor is allowed to read it.
     *
     * <p>No {@code karma}, no {@code comments} (#248): both are F5, and a field that promises them
     * teaches the frontend to ask for a number that is not there yet.
     *
     * @param targetId whose profile this is
     * @param actorId  who is asking, always from the token (#121)
     * @return the profile
     * @throws NotFoundException if the target does not exist, or the actor may not see them - the two
     *                           answer identically, on purpose (#249)
     */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String targetId, String actorId) {
        User target = userService.getById(targetId);
        visibilityService.requireVisible(targetId, actorId);

        Set<String> roles = userRoleRepository.findActiveRoleNames(targetId);
        AttendanceSummaryResponse attendance = tableSessionService.summarizeAll(targetId);
        return new ProfileResponse(target.getId(), target.getName(), target.getCountry(), roles, attendance);
    }
}
