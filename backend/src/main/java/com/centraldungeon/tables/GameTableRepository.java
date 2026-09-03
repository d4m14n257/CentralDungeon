package com.centraldungeon.tables;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code game_tables} table. */
public interface GameTableRepository extends JpaRepository<GameTable, String> {

    /**
     * /game-tables (the public explorer): a master never sees a table they themselves run in the
     * list meant for applying as a Player - a table has exactly one set of people who play at it
     * and a disjoint set who run it (decisiones.md #154).
     *
     * @param statuses which statuses count as publicly visible
     * @param actorId  the actor, from the token. It is in the WHERE, which is the point (#121)
     * @param pageable page, size and sort
     * @return one page of tables the actor could apply to
     */
    @Query("select t from GameTable t where t.status in :statuses and not exists "
            + "(select 1 from Master m where m.gameTable = t and m.user.id = :actorId)")
    Page<GameTable> findByStatusInAndNotMasteredByActor(
            @Param("statuses") Collection<GameTableStatus> statuses, @Param("actorId") String actorId, Pageable pageable);

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
     * /admin/tables: management listing, unfiltered by pertenencia - the caller is already an admin.
     *
     * @param statuses the statuses to show; the controller defaults them to the ones waiting on an
     *                 admin (#176)
     * @param pageable page, size and sort
     * @return one page of tables in those statuses
     */
    Page<GameTable> findByStatusIn(Collection<GameTableStatus> statuses, Pageable pageable);

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
