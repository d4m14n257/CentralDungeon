package com.centraldungeon.tables;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who runs a table, and the invariant that there is exactly one live Primary per table (#73).
 *
 * <p>MySQL has no partial unique index, so that invariant cannot be a constraint: it is held here,
 * behind a pessimistic lock on the table's master rows, and covered by {@code MasterServiceIT}.
 *
 * <p>This is also where pertenencia is answered from. A row here - not the {@code Master} role - is
 * what authorizes acting on a concrete table (#135), which is why {@link #isMasterOf} and
 * {@link #isPrimaryOf} are read by half the transitions in {@code GameTableService}.
 */
@Service
public class MasterService {

    /** The statuses that mean somebody is already involved with a table as a player. */
    private static final List<TableRegistrationStatus> ACTIVE_APPLICATION_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** The {@code masters} table. */
    private final MasterRepository masterRepository;

    /** Used to refuse making somebody a master of a table they already play at (#154). */
    private final TableRegistrationRepository registrationRepository;

    /** Resolves the people being added. */
    private final UserService userService;

    /**
     * @param masterRepository       the {@code masters} table
     * @param registrationRepository used to check nobody is made master of a table they play at
     * @param userService            resolves the people being added
     */
    public MasterService(MasterRepository masterRepository, TableRegistrationRepository registrationRepository, UserService userService) {
        this.masterRepository = masterRepository;
        this.registrationRepository = registrationRepository;
        this.userService = userService;
    }

    /**
     * Makes the creator of a table its Primary. Called right after the table is saved, which is what
     * turns "created it" into the pertenencia that authorizes running it (#73, #135).
     *
     * @param gameTable the freshly created table
     * @param creator   its author
     * @return the master row
     */
    @Transactional
    public Master createPrimary(GameTable gameTable, User creator) {
        return masterRepository.save(new Master(gameTable, creator, MasterType.Primary));
    }

    /**
     * Only the current Primary may add a Secondary or hand off Primary itself (modelo-datos.md #73).
     * The row lock on the table's existing masters serializes concurrent calls so exactly one
     * Primary survives even under a race - the invariant MySQL cannot enforce with a partial
     * unique index.
     */
    @Transactional
    public void addOrPromote(GameTable gameTable, String actorId, String targetUserId, MasterType requestedType) {
        List<Master> masters = masterRepository.findByGameTableIdForUpdate(gameTable.getId());

        Master actorMaster = masters.stream()
                .filter(m -> m.getUser().getId().equals(actorId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenActionException("Only a master of this table can manage its masters"));
        if (actorMaster.getMasterType() != MasterType.Primary) {
            throw new ForbiddenActionException("Only the Primary master can add or promote masters");
        }

        Master target = masters.stream()
                .filter(m -> m.getUser().getId().equals(targetUserId))
                .findFirst()
                .orElseGet(() -> {
                    if (registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                            gameTable.getId(), targetUserId, ACTIVE_APPLICATION_STATUSES)) {
                        throw new ConflictException("A player of this table cannot be made its master");
                    }
                    return masterRepository.save(new Master(gameTable, userService.getById(targetUserId), MasterType.Secondary));
                });

        if (requestedType == MasterType.Primary) {
            if (!target.getUser().getId().equals(actorId)) {
                actorMaster.setMasterType(MasterType.Secondary);
            }
            target.setMasterType(MasterType.Primary);
        } else {
            target.setMasterType(MasterType.Secondary);
        }
    }

    /**
     * Bootstraps the very first masters of a table an admin created Unassigned (modelo-datos.md #72).
     * Cannot reuse addOrPromote: that method assumes a Primary already exists to authorize the
     * caller, which is exactly what is missing on an Unassigned table.
     */
    @Transactional
    public void assignInitialMasters(GameTable gameTable, String primaryUserId, List<String> secondaryUserIds) {
        List<Master> existing = masterRepository.findByGameTableIdForUpdate(gameTable.getId());
        if (!existing.isEmpty()) {
            throw new ConflictException("Table already has masters assigned");
        }
        if (secondaryUserIds.contains(primaryUserId)) {
            throw new ConflictException("Primary and Secondary cannot be the same user");
        }

        masterRepository.save(new Master(gameTable, userService.getById(primaryUserId), MasterType.Primary));
        for (String secondaryId : secondaryUserIds) {
            masterRepository.save(new Master(gameTable, userService.getById(secondaryId), MasterType.Secondary));
        }
    }

    /**
     * The cascade of a table's logical delete (#25): its master rows go down with it (#175).
     *
     * <p>Marked, never dropped - the record of who ran a table outlives the table itself.
     *
     * @param gameTableId the table being deleted
     * @param deletedAt   the same timestamp stamped on the table, so the cascade is one event
     */
    @Transactional
    public void softDeleteAllOfTable(String gameTableId, LocalDateTime deletedAt) {
        for (Master master : masterRepository.findByGameTable_Id(gameTableId)) {
            master.setStatus(MasterRowStatus.Deleted);
            master.setDeletedAt(deletedAt);
        }
    }

    /**
     * Everyone who runs a table.
     *
     * @param gameTableId the table
     * @return its master rows
     */
    @Transactional(readOnly = true)
    public List<Master> findByGameTable(String gameTableId) {
        return masterRepository.findByGameTable_Id(gameTableId);
    }

    /**
     * Pertenencia: does this person run this table, in any capacity.
     *
     * <p>This, and not {@code hasRole('MASTER')}, is what authorizes acting on a concrete table
     * (#135). The role only says somebody may create tables of their own.
     *
     * @param gameTableId the table
     * @param userId      the actor, always from the token (#121)
     * @return true when they have a master row on it
     */
    @Transactional(readOnly = true)
    public boolean isMasterOf(String gameTableId, String userId) {
        return masterRepository.findByGameTable_IdAndUser_Id(gameTableId, userId).isPresent();
    }

    /**
     * The narrower check: is this person the table's Primary, rather than one of its co-masters.
     * What the lifecycle transitions reserved to the owner ask (#71).
     *
     * @param gameTableId the table
     * @param userId      the actor, always from the token (#121)
     * @return true when their master row is the Primary one
     */
    @Transactional(readOnly = true)
    public boolean isPrimaryOf(String gameTableId, String userId) {
        return masterRepository
                .findByGameTable_IdAndUser_Id(gameTableId, userId)
                .map(master -> master.getMasterType() == MasterType.Primary)
                .orElse(false);
    }
}
