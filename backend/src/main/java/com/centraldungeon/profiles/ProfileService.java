package com.centraldungeon.profiles;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.profiles.dto.ProfileResponse;
import com.centraldungeon.profiles.dto.UserTableResponse;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.tables.MasterRowStatus;
import com.centraldungeon.tables.TableSessionService;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Somebody's profile, assembled once {@link ProfileVisibilityService} has decided the actor may read
 * it (arquitectura §4.5).
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

    /** The tables a person runs, for an admin's view of their record (#284). */
    private final MasterRepository masterRepository;

    /** The tables a person applied to or plays at, and how many they finished (#284). */
    private final TableRegistrationRepository registrationRepository;

    /**
     * The registration statuses an admin's view of a person's tables lists: everything but a
     * withdrawn application, which is soft-deleted and invisible on every read path (#25).
     */
    private static final List<TableRegistrationStatus> LISTED_REGISTRATIONS = List.of(
            TableRegistrationStatus.Candidate, TableRegistrationStatus.Player,
            TableRegistrationStatus.Rejected, TableRegistrationStatus.Blocked);

    /**
     * @param userService          loads the person a profile describes
     * @param userRoleRepository   the roles a profile lists
     * @param tableSessionService  the aggregate attendance a profile shows
     * @param visibilityService    the visibility rules, all five, in one place
     * @param masterRepository     the tables a person runs
     * @param registrationRepository the tables a person applied to or plays at
     */
    public ProfileService(
            UserService userService,
            UserRoleRepository userRoleRepository,
            TableSessionService tableSessionService,
            ProfileVisibilityService visibilityService,
            MasterRepository masterRepository,
            TableRegistrationRepository registrationRepository) {
        this.userService = userService;
        this.userRoleRepository = userRoleRepository;
        this.tableSessionService = tableSessionService;
        this.visibilityService = visibilityService;
        this.masterRepository = masterRepository;
        this.registrationRepository = registrationRepository;
    }

    /**
     * Somebody's profile, as the actor is allowed to read it.
     *
     * <p>No {@code karma}, no {@code comments} (#248): both are F5, and a field that promises them
     * teaches the frontend to ask for a number that is not there yet. The Discord handle and the count
     * of finished tables are what a master reads on a candidate's card instead (#284).
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
        long finishedTables = registrationRepository.countByUser_IdAndStatusAndGameTable_Status(
                targetId, TableRegistrationStatus.Player, GameTableStatus.Finished);
        return new ProfileResponse(
                target.getId(), target.getDiscordUsername(), target.getName(), target.getCountry(), roles, attendance, finishedTables);
    }

    /**
     * Every table a person is linked to, for an admin reading their record (#284): the ones they run
     * and the ones they applied to or play at, in any status but removed.
     *
     * <p><b>Admins only</b> - the controller enforces it - and with no visibility rule in between: the
     * admin has none (#45). It answers what the admin needs to judge an account: where this person is
     * and in what capacity, with each table one click away.
     *
     * <p><b>Merged and paged in memory</b>, the precedent of the shared tray (#100): the rows come from
     * two tables ({@code masters} and {@code table_registrations}) that no single query can page
     * together, and one person's tables are a handful, not thousands. Newest table first, by id on a
     * tie (#173).
     *
     * @param targetId whose tables these are
     * @param pageable which page, and its size; the sort is fixed
     * @return one page of their tables
     * @throws NotFoundException if nobody has that id
     */
    @Transactional(readOnly = true)
    public PageResponse<UserTableResponse> listTablesForAdmin(String targetId, Pageable pageable) {
        userService.getById(targetId);

        List<UserTableResponse> rows = new ArrayList<>();
        masterRepository.findLiveByUser(targetId, MasterRowStatus.Created).stream()
                .filter(master -> master.getGameTable().getStatus() != GameTableStatus.Deleted)
                .forEach(master -> rows.add(row(master.getGameTable(), master.getMasterType().name())));
        registrationRepository.findWithTableByUserAndStatusIn(targetId, LISTED_REGISTRATIONS).stream()
                .filter(registration -> registration.getGameTable().getStatus() != GameTableStatus.Deleted)
                .forEach(registration -> rows.add(row(registration.getGameTable(), registration.getStatus().name())));
        rows.sort(Comparator.comparing(UserTableResponse::tableCreatedAt, Comparator.reverseOrder())
                .thenComparing(UserTableResponse::tableId));

        int size = pageable.getPageSize();
        int from = Math.min((int) pageable.getOffset(), rows.size());
        int to = Math.min(from + size, rows.size());
        int totalPages = size == 0 ? 0 : (rows.size() + size - 1) / size;
        return new PageResponse<>(rows.subList(from, to), pageable.getPageNumber(), size, rows.size(), totalPages);
    }

    private static UserTableResponse row(GameTable table, String relation) {
        return new UserTableResponse(table.getId(), table.getName(), table.getStatus().name(), relation, table.getCreatedAt());
    }
}
