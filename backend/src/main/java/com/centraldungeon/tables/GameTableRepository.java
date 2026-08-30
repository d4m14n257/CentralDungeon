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

    Page<GameTable> findByStatusIn(Collection<GameTableStatus> statuses, Pageable pageable);

    /** /master/tables: every status, including Preparation - a master needs to see and open their own drafts. */
    @Query("select m.gameTable from Master m where m.user.id = :userId")
    Page<GameTable> findByMasterUserId(@Param("userId") String userId, Pageable pageable);

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
