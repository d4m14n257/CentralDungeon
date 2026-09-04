package com.centraldungeon.tables;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code masters} table - the source of truth for who runs a table (#135). */
public interface MasterRepository extends JpaRepository<Master, MasterId> {

    /**
     * Everyone who runs a table.
     *
     * @param gameTableId the table
     * @return its master rows, live and deleted alike; the caller filters by status
     */
    List<Master> findByGameTable_Id(String gameTableId);

    /**
     * Everyone who runs a table <em>right now</em> - the deleted rows left out.
     *
     * @param gameTableId the table
     * @param status      the row status to keep, always {@code Created} in production code
     * @return its live master rows
     */
    List<Master> findByGameTable_IdAndStatus(String gameTableId, MasterRowStatus status);

    /**
     * Reads one master row whatever its status, so a row that was removed can be brought back
     * instead of inserted twice - the composite key would reject the second insert.
     *
     * @param gameTableId the table
     * @param userId      the person
     * @return their row, live or deleted, or empty when they were never a master of it
     */
    Optional<Master> findByGameTable_IdAndUser_Id(String gameTableId, String userId);

    /**
     * The membership check behind every "may this person touch this table" decision (#121, #135).
     *
     * <p>It filters by status on purpose: a co-master who was removed keeps their row - the record
     * of who ran a table outlives the table (#175) - and answering membership from a dead row would
     * leave them authorized forever.
     *
     * @param gameTableId the table
     * @param userId      the actor, always taken from the token and never from the URL
     * @param status      the row status to require, always {@code Created} in production code
     * @return their live master row, or empty when they do not run this table
     */
    Optional<Master> findByGameTable_IdAndUser_IdAndStatus(String gameTableId, String userId, MasterRowStatus status);

    /**
     * Whether someone runs any table at all - what the context switcher uses to decide if the Master
     * context is worth offering (#135). Removed rows do not count, for the same reason
     * {@link #findByGameTable_IdAndUser_IdAndStatus} filters them.
     *
     * @param userId the person to check
     * @param status the row status to require, always {@code Created} in production code
     * @return true when they have at least one live master row
     */
    boolean existsByUser_IdAndStatus(String userId, MasterRowStatus status);

    /**
     * Every table the person runs right now, whatever the table's own state. What the master
     * dashboard starts from before asking each table what it is waiting on (#136).
     *
     * @param userId the actor, always from the token (#121)
     * @param status the row status to require, always {@code Created} in production code
     * @return their live master rows, with the table already fetched
     */
    @Query("select m from Master m join fetch m.gameTable where m.user.id = :userId and m.status = :status")
    List<Master> findLiveByUser(@Param("userId") String userId, @Param("status") MasterRowStatus status);

    /**
     * Row-locked read used by MasterService before flipping who is Primary (modelo-datos.md #73):
     * concurrent requests on the same table serialize here instead of racing past each other.
     *
     * @param gameTableId the table whose master rows are being changed
     * @return its master rows, locked for the rest of the transaction
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Master m where m.gameTable.id = :gameTableId")
    List<Master> findByGameTableIdForUpdate(@Param("gameTableId") String gameTableId);
}
