package com.centraldungeon.tables;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameTableRepository extends JpaRepository<GameTable, String> {

    /**
     * /game-tables (the public explorer): a master never sees a table they themselves run in the
     * list meant for applying as a Player - a table has exactly one set of people who play at it
     * and a disjoint set who run it (decisiones.md #154).
     */
    @Query("select t from GameTable t where t.status in :statuses and not exists "
            + "(select 1 from Master m where m.gameTable = t and m.user.id = :actorId)")
    Page<GameTable> findByStatusInAndNotMasteredByActor(
            @Param("statuses") Collection<GameTableStatus> statuses, @Param("actorId") String actorId, Pageable pageable);

    /**
     * /master/tables: every status, including Preparation - a master needs to see and open their own
     * drafts. Deleted is the exception: a soft-deleted table is gone for everyone (#25, #175).
     */
    @Query("select m.gameTable from Master m where m.user.id = :userId "
            + "and m.gameTable.status <> com.centraldungeon.tables.GameTableStatus.Deleted")
    Page<GameTable> findByMasterUserId(@Param("userId") String userId, Pageable pageable);

    /** /admin/tables: management listing, unfiltered by pertenencia - the caller is already an admin. */
    Page<GameTable> findByStatusIn(Collection<GameTableStatus> statuses, Pageable pageable);

    /**
     * Locks the table aggregate root for the invariants MySQL cannot express as a constraint:
     * one active registration per pair (#28) and the max_players cap / auto-reject on fill (#34).
     * There is no natural row to lock for "no active registration yet exists", so registrations
     * locks the table itself and serializes on it instead.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from GameTable t where t.id = :id")
    Optional<GameTable> findByIdForUpdate(@Param("id") String id);
}
