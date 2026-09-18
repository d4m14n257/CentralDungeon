package com.centraldungeon.tables;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes the {@code game_tables} table.
 *
 * <p>{@code JpaSpecificationExecutor} is here for the explorer: it combines the visibility rules with
 * a search box whose shape - how many criteria, joined by which connectors - is only known at runtime
 * (arquitectura.md 2.2), the same reason the catalogs and the files needed it before.
 */
public interface GameTableRepository extends JpaRepository<GameTable, String>, JpaSpecificationExecutor<GameTable> {

    /**
     * /master/tables: every status, including Preparation - a master needs to see and open their own
     * drafts. Deleted is the exception: a soft-deleted table is gone for everyone (#25, #175).
     *
     * @param userId   the actor, from the token
     * @param pageable page, size and sort
     * @return one page of the tables they run
     */
    @Query("select m.gameTable from Master m where m.user.id = :userId "
            + "and m.gameTable.status <> com.centraldungeon.tables.GameTableStatus.Deleted")
    Page<GameTable> findByMasterUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * The tables somebody runs, in the statuses that count as a live commitment - half of what the
     * clash rules of #178 compare against. Not paginated: it feeds a computation, not a screen.
     *
     * <p>Only live master rows count. Somebody whose master row fell with a deleted table (#175) no
     * longer occupies that stretch of the week.
     *
     * @param userId   the person whose commitments are being collected, always from the token (#121)
     * @param statuses the statuses that count as committed. {@code Pause} is deliberately not among
     *                 them: a paused table freezes its agenda and does not reserve the slot (#32, #178)
     * @return the tables they run in those statuses
     */
    @Query("select m.gameTable from Master m where m.user.id = :userId "
            + "and m.status = com.centraldungeon.tables.MasterRowStatus.Created "
            + "and m.gameTable.status in :statuses")
    List<GameTable> findMasteredByUserInStatuses(
            @Param("userId") String userId, @Param("statuses") Collection<GameTableStatus> statuses);

    /**
     * This source's contribution to the shared admin tray: the tables sitting in review that are
     * either free or already the reader's (#100).
     *
     * <p>The twin of {@code ApprovalRequestRepository.findQueueItems}, and the reservation rule is
     * the same one: {@code claimed_by is null or claimed_by = :actorId}, so a table another admin
     * took is not in this reader's tray at all (modelo-datos.md §5).
     *
     * <p><b>One status and only one.</b> The caller passes {@code Preparation} - a table that was
     * sent to review - and nothing else qualifies: {@code Draft} was never sent, {@code
     * ChangesRequested} is back with its master, and {@code Unassigned} is waiting for a master
     * rather than for a review (#245).
     *
     * @param status   the status that means "waiting on an admin", always {@code Preparation}
     * @param actorId  the admin reading the tray, always from the token (#121)
     * @param pageable the per-source ceiling; the tray pages after the merge
     * @return the tables waiting for this admin, oldest first
     */
    @Query("""
            select t from GameTable t
            where t.status = :status
              and (t.claimedBy is null or t.claimedBy.id = :actorId)
            order by t.createdAt asc, t.id asc
            """)
    List<GameTable> findQueueItems(
            @Param("status") GameTableStatus status, @Param("actorId") String actorId, Pageable pageable);

    /**
     * The reservations that went stale - what the release job hands back (#100).
     *
     * <p>Bounded to the tables still in review for the same reason its twin is: a table that was
     * approved or sent back is in nobody's tray, and clearing its {@code claimed_by} would be a write
     * with no reader.
     *
     * @param status   the status that means "still waiting", always {@code Preparation}
     * @param cutoff   reservations taken before this instant are expired
     * @param pageable the batch bound
     * @return the expired reservations, oldest first
     */
    @Query("""
            select t from GameTable t
            where t.status = :status
              and t.claimedAt is not null
              and t.claimedAt < :cutoff
            order by t.claimedAt asc
            """)
    List<GameTable> findExpiredClaims(
            @Param("status") GameTableStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    // findByStatusIn used to back /admin/tables. It is gone since F3.3: that listing now accepts the
    // ?q= of #164 as well as a status filter, so it goes through findAll(Specification, Pageable)
    // like every other search in the application, and a derived query nobody calls is dead weight.

    /**
     * Locks the table aggregate root for the invariants MySQL cannot express as a constraint:
     * one active registration per pair (#28) and the max_players cap / auto-reject on fill (#34).
     * There is no natural row to lock for "no active registration yet exists", so registrations
     * locks the table itself and serializes on it instead.
     *
     * @param id the table to lock
     * @return the table, locked for the rest of the transaction, or empty if it does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from GameTable t where t.id = :id")
    Optional<GameTable> findByIdForUpdate(@Param("id") String id);
}
