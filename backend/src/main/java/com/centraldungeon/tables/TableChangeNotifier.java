package com.centraldungeon.tables;

import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.notifications.TableMembership;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides who hears that a table changed, and which notification each of them gets.
 *
 * <p>"Joined to a table" means its live masters, Primary and Secondary, and its accepted players.
 * Candidates are not: they have no session to lose and their own screen already shows their
 * application. Every method here follows the same three rules.
 *
 * <ul>
 *   <li><b>The actor never hears about their own change.</b> Only the actor is left out, not every
 *       master: when a master acts, the co-masters are told.
 *   <li><b>A master gets the {@code …ByAdmin} variant when the actor does not run the table</b> - an admin
 *       acting on somebody else's table - and the plain type otherwise. A player always gets the plain
 *       type, because to a player it does not matter who pressed the button.
 *   <li><b>The recipient's membership travels with the notification</b>, because the same plain type
 *       reaches masters and players and their click lands on different screens.
 * </ul>
 */
@Component
public class TableChangeNotifier {

    /** The statuses in which a table is public and has, or may have, people joined to it. */
    private static final Set<GameTableStatus> PUBLIC_STATUSES = Set.of(
            GameTableStatus.Opened, GameTableStatus.InProgress, GameTableStatus.PauseRequested, GameTableStatus.Pause);

    /** The variant a master hears when an admin who does not run the table made the change. */
    private static final Map<NotificationType, NotificationType> BY_ADMIN = Map.of(
            NotificationType.TablePaused, NotificationType.TablePausedByAdmin,
            NotificationType.TableResumed, NotificationType.TableResumedByAdmin,
            NotificationType.TableCanceled, NotificationType.TableCanceledByAdmin);

    /** Writes the notifications. */
    private final NotificationService notificationService;

    /** Who runs the table. */
    private final MasterService masterService;

    /** Who plays at the table. */
    private final TableRegistrationRepository registrationRepository;

    /**
     * @param notificationService    writes the notifications
     * @param masterService          the live masters of a table
     * @param registrationRepository the accepted players of a table
     */
    public TableChangeNotifier(
            NotificationService notificationService,
            MasterService masterService,
            TableRegistrationRepository registrationRepository) {
        this.notificationService = notificationService;
        this.masterService = masterService;
        this.registrationRepository = registrationRepository;
    }

    /**
     * Tells everybody joined to the table that it was paused, resumed, cancelled or finished.
     *
     * @param table       the table that changed, already in its new status
     * @param plain       {@link NotificationType#TablePaused}, {@link NotificationType#TableResumed},
     *                    {@link NotificationType#TableCanceled} or {@link NotificationType#TableFinished}
     * @param actorId     who made the change, from the token. Never notified
     * @param requesterId the master who asked for the change when somebody else decided it - a
     *                    pause a master requested and an admin approved - and who was already told
     *                    through the resolution of their request. Null when nobody asked
     */
    @Transactional
    public void announceTransition(GameTable table, NotificationType plain, String actorId, @Nullable String requesterId) {
        Set<String> told = new HashSet<>();
        told.add(actorId);
        if (requesterId != null) {
            told.add(requesterId);
        }
        List<Master> masters = masterService.findByGameTable(table.getId());
        NotificationType masterType = runsTable(masters, actorId) ? plain : BY_ADMIN.getOrDefault(plain, plain);
        tellMasters(masters, told, userId -> notificationService.notifyTableChanged(userId, table, masterType, TableMembership.Master));
        tellPlayers(table, told, userId -> notificationService.notifyTableChanged(userId, table, plain, TableMembership.Player));
    }

    /**
     * Tells everybody joined to a public table that its weekly agenda changed.
     *
     * <p>A table that never went public has nobody to tell: its masters wrote the agenda themselves, or
     * an admin's edit already reaches them through {@link #announceEdit}.
     *
     * @param table   the table whose agenda changed
     * @param actorId who changed it, from the token. Never notified
     */
    @Transactional
    public void announceScheduleChanged(GameTable table, String actorId) {
        if (!PUBLIC_STATUSES.contains(table.getStatus())) {
            return;
        }
        Set<String> told = new HashSet<>();
        told.add(actorId);
        tellMasters(masterService.findByGameTable(table.getId()), told, userId -> notificationService.notifyTableChanged(
                userId, table, NotificationType.TableScheduleChanged, TableMembership.Master));
        tellPlayers(table, told, userId -> notificationService.notifyTableChanged(
                userId, table, NotificationType.TableScheduleChanged, TableMembership.Player));
    }

    /**
     * Tells the people joined to a table that an admin rewrote it.
     *
     * <p>The masters get one {@code TableEditedByAdmin} that lists what changed, agenda included. The
     * players only care about the agenda, so they hear {@code TableScheduleChanged} when it moved and
     * nothing otherwise - a corrected description is not news for the people already playing. An edit
     * that changed nothing tells nobody.
     *
     * @param table   the table the admin rewrote
     * @param actorId the admin, from the token. Never notified
     * @param changes the codes of what changed, agenda included; empty when nothing did
     */
    @Transactional
    public void announceEdit(GameTable table, String actorId, List<String> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Set<String> told = new HashSet<>();
        told.add(actorId);
        tellMasters(masterService.findByGameTable(table.getId()), told,
                userId -> notificationService.notifyTableEditedByAdmin(userId, table, changes));
        if (changes.contains(TableEditDiff.SCHEDULE) && PUBLIC_STATUSES.contains(table.getStatus())) {
            tellPlayers(table, told, userId -> notificationService.notifyTableChanged(
                    userId, table, NotificationType.TableScheduleChanged, TableMembership.Player));
        }
    }

    /**
     * Tells the masters a table had that an admin removed it.
     *
     * <p>Keeps the rule it always had: an admin who also ran the table is acting on their own, and
     * nobody is told. A table that can be removed never had players.
     *
     * @param table   the removed table
     * @param actorId who removed it, from the token
     * @param masters the masters the table had, read before the removal marked them gone
     */
    @Transactional
    public void announceDeleted(GameTable table, String actorId, List<Master> masters) {
        if (runsTable(masters, actorId)) {
            return;
        }
        tellMasters(masters, new HashSet<>(), userId -> notificationService.notifyTableDeleted(userId, table));
    }

    private boolean runsTable(List<Master> masters, String userId) {
        return masters.stream().anyMatch(master -> master.getUser().getId().equals(userId));
    }

    /** Each person is told once: {@code told} starts with whoever must be skipped and grows as people are reached. */
    private void tellMasters(List<Master> masters, Set<String> told, Consumer<String> tell) {
        for (Master master : masters) {
            String userId = master.getUser().getId();
            if (told.add(userId)) {
                tell.accept(userId);
            }
        }
    }

    private void tellPlayers(GameTable table, Set<String> told, Consumer<String> tell) {
        for (TableRegistration registration : registrationRepository
                .findByGameTable_IdAndStatusOrderByCreatedAtAsc(table.getId(), TableRegistrationStatus.Player)) {
            String userId = registration.getUser().getId();
            if (told.add(userId)) {
                tell.accept(userId);
            }
        }
    }
}
