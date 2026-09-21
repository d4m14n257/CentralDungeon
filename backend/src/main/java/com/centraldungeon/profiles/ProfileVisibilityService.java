package com.centraldungeon.profiles;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.settings.SettingsService;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.tables.MasterRowStatus;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The five rules of "Visibilidad de perfiles" (modelo-datos.md §5: #41, #44, #45, #47), written a
 * year ago and never implemented until now - in **one place**, {@link #requireVisible}, cheapest
 * check first.
 *
 * <p>The whole rule set answers one question: given the table that links two people, is that link
 * still open? A table that never published anything never opens it (#41a); one that has, or is
 * running, or is paused, keeps it open for as long as it has not closed; once closed, it stays open
 * for two more weeks and then it does not (#44). That single idea - {@link #isLinkStillOpen} - is
 * reused by all three of the table-shaped rules (#41a, #41b, #47), which is why a special case for
 * {@code Pause} is never written: {@code closed_at} is null while the table is paused exactly as it
 * is while the table is running, so the clock never starts in the first place.
 *
 * <p>Lives next to {@link ProfileService} rather than folded into it because assembling a profile and
 * deciding whether it may be read are two different questions with two different sets of collaborators
 * - {@link ProfileService} does not otherwise need {@code Master} or {@code TableRegistration} at
 * all.
 */
@Service
public class ProfileVisibilityService {


    /**
     * States a table's own master row can never be seen through (#41a). Every other status - Opened,
     * InProgress, PauseRequested, Pause, Finished, Canceled - has been out where somebody could look
     * at it at some point, which is the premise {@link #isLinkStillOpen} builds on. {@code Deleted} is
     * here for the same reason it is everywhere else: a soft-deleted table is gone for everyone
     * (#25, #175).
     */
    private static final Set<GameTableStatus> NEVER_PUBLISHED = EnumSet.of(
            GameTableStatus.Draft, GameTableStatus.Unassigned, GameTableStatus.Preparation,
            GameTableStatus.ChangesRequested, GameTableStatus.Deleted);

    /** The statuses that mean somebody is actually, actively involved with a table (#41b, #47). */
    private static final List<TableRegistrationStatus> ACTIVE_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** Who runs which table, and since when - the source rule #41a and #41b both read. */
    private final MasterRepository masterRepository;

    /** Who applied where, and who plays where - #41b and #47. */
    private final TableRegistrationRepository registrationRepository;

    /** Resolves the tables two players share, once their ids are known (#47). */
    private final GameTableRepository gameTableRepository;

    /** Answers #45: whether the actor is an Admin or the platform Owner. */
    private final UserRoleRepository userRoleRepository;

    /**
     * How long a closed table keeps a profile visible through it - {@code
     * profiles.visibility_window_days} in {@code system_settings} (#44, #141).
     *
     * <p><b>It was a constant of this class until F3.5</b>, and it is the one setting of that slice
     * whose change is retroactive: nothing is recomputed and nothing is stored, so lowering the
     * number takes visibility away from people who have it at this instant and raising it hands it
     * back to people who had already lost it. That is a property of #44 itself - the rule is
     * evaluated against {@code closed_at} on every read - and it is why {@code /admin/settings} warns
     * before saving this one and not the other three.
     */
    private final SettingsService settingsService;

    /**
     * @param masterRepository       who runs which table (#41a, #41b)
     * @param registrationRepository who applied, and who plays (#41b, #47)
     * @param gameTableRepository    resolves the tables two players share (#47)
     * @param userRoleRepository     the actor's global roles, for #45
     * @param settingsService        the visibility window, editable without a deploy (#44, #141)
     */
    public ProfileVisibilityService(
            MasterRepository masterRepository,
            TableRegistrationRepository registrationRepository,
            GameTableRepository gameTableRepository,
            UserRoleRepository userRoleRepository,
            SettingsService settingsService) {
        this.masterRepository = masterRepository;
        this.registrationRepository = registrationRepository;
        this.gameTableRepository = gameTableRepository;
        this.userRoleRepository = userRoleRepository;
        this.settingsService = settingsService;
    }

    /**
     * The one gate every profile read goes through.
     *
     * @param targetId whose profile is being read
     * @param actorId  who is reading it, always from the token (#121)
     * @throws NotFoundException if the actor may not see this profile - never a {@code 403} (#249):
     *                           the same reason a vetoed table and a deleted one both answer 404
     *                           (#29, #175) is why this does too. A {@code 403} would confirm that
     *                           the target exists and that a relationship once linked them, which is
     *                           precisely what the two-week window of #44 is meant to stop confirming
     */
    @Transactional(readOnly = true)
    public void requireVisible(String targetId, String actorId) {
        if (targetId.equals(actorId)) {
            return;
        }
        if (isAdminOrOwner(actorId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (runsAVisibleTable(targetId, now)) {
            return;
        }
        if (appliedToATableTheActorRuns(targetId, actorId, now)) {
            return;
        }
        if (sharesATableAsPlayer(targetId, actorId, now)) {
            return;
        }
        throw new NotFoundException("User not found: " + targetId);
    }

    /** #45: an Admin or the platform Owner has no restriction here. */
    private boolean isAdminOrOwner(String actorId) {
        Set<String> roles = userRoleRepository.findActiveRoleNames(actorId);
        return roles.contains(PlatformRole.ADMIN.roleName()) || roles.contains(PlatformRole.OWNER.roleName());
    }

    /**
     * #41a: the target's profile is visible for anybody with a session while they run at least one
     * table whose link is still open.
     *
     * <p>Reads every master row the target ever held, live and deleted alike, and filters here rather
     * than in the query: a deleted row grants nothing (#216), and that has to be a line of Java
     * somebody can read, not a silent {@code WHERE}.
     */
    private boolean runsAVisibleTable(String targetId, LocalDateTime now) {
        for (Master master : masterRepository.findByUser_Id(targetId)) {
            if (master.getStatus() == MasterRowStatus.Created && isLinkStillOpen(master.getGameTable(), now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * #41b: the actor runs a table the target applied to. "Since it receives the application" is
     * literal - {@code Candidate} counts exactly as much as {@code Player}, and there is no
     * acceptance to wait for.
     */
    private boolean appliedToATableTheActorRuns(String targetId, String actorId, LocalDateTime now) {
        List<Master> run = masterRepository.findLiveByUser(actorId, MasterRowStatus.Created);
        if (run.isEmpty()) {
            return false;
        }
        Map<String, GameTable> mastered = new HashMap<>();
        for (Master master : run) {
            mastered.put(master.getGameTable().getId(), master.getGameTable());
        }

        List<TableRegistration> applications =
                registrationRepository.findByGameTable_IdInAndUser_IdAndStatusIn(mastered.keySet(), targetId, ACTIVE_STATUSES);
        for (TableRegistration application : applications) {
            GameTable table = mastered.get(application.getGameTable().getId());
            if (table != null && isLinkStillOpen(table, now)) {
                return true;
            }
        }
        return false;
    }

    /** #47: the actor and the target both hold a live {@code Player} registration on the same table. */
    private boolean sharesATableAsPlayer(String targetId, String actorId, LocalDateTime now) {
        Set<String> actorTables = tableIdsOf(registrationRepository.findByUser_IdAndStatus(actorId, TableRegistrationStatus.Player));
        if (actorTables.isEmpty()) {
            return false;
        }
        Set<String> targetTables = tableIdsOf(registrationRepository.findByUser_IdAndStatus(targetId, TableRegistrationStatus.Player));
        actorTables.retainAll(targetTables);
        if (actorTables.isEmpty()) {
            return false;
        }

        for (GameTable table : gameTableRepository.findAllById(actorTables)) {
            if (isLinkStillOpen(table, now)) {
                return true;
            }
        }
        return false;
    }

    /** The distinct tables a set of registrations point to. Reading a lazy id never triggers a query. */
    private static Set<String> tableIdsOf(List<TableRegistration> registrations) {
        Set<String> ids = new HashSet<>();
        for (TableRegistration registration : registrations) {
            ids.add(registration.getGameTable().getId());
        }
        return ids;
    }

    /**
     * #44, applied to whichever table links two people: is that link still open?
     *
     * <p>A table that never published stays closed regardless of its dates. One that has - running,
     * paused, or finished within the window - is open. There is no branch for {@code Pause}: its
     * {@code closed_at} is null exactly like a live table's, so the clock simply has not started, and
     * writing a case for it would only be restating that fact.
     */
    private boolean isLinkStillOpen(GameTable table, LocalDateTime now) {
        if (NEVER_PUBLISHED.contains(table.getStatus())) {
            return false;
        }
        LocalDateTime closedAt = table.getClosedAt();
        return closedAt == null || !closedAt.isBefore(now.minusDays(settingsService.profileVisibilityWindowDays()));
    }
}
