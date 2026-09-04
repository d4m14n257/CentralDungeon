package com.centraldungeon.tables;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes {@code table_sessions} - a table's materialized calendar (#33). */
public interface TableSessionRepository extends JpaRepository<TableSession, String> {

    /**
     * A table's whole calendar, in the order it is played and read.
     *
     * <p>By {@code sequence_number} and not by {@code scheduled_at}: a corrected date can push a
     * session past the next one, and the run's numbering is what the master and the players talk
     * about ("we missed the fourth").
     *
     * @param gameTableId the table
     * @return every session of the table, first to last
     */
    List<TableSession> findByGameTable_IdOrderBySequenceNumberAsc(String gameTableId);

    /**
     * A table's sessions in one status, in playing order. With {@code Scheduled} it is what a resume
     * re-lays (#33) and what a pause hides.
     *
     * @param gameTableId the table
     * @param status      the status to match
     * @return its sessions in that status, first to last
     */
    List<TableSession> findByGameTable_IdAndStatusOrderBySequenceNumberAsc(String gameTableId, TableSessionStatus status);

    /**
     * Whether the table's calendar was already materialized. It is what makes
     * {@code TableSessionService.materialize} idempotent: opening a table twice - which the
     * lifecycle does not allow today but a retry could - must not double its sessions.
     *
     * @param gameTableId the table
     * @return true when it already has at least one session
     */
    boolean existsByGameTable_Id(String gameTableId);

    /**
     * The highest {@code sequence_number} the table has used. The replacement session of #194 takes
     * the next one, so it can never collide with the row it is replacing.
     *
     * @param gameTableId the table
     * @return the highest number in use, or null when the table has no sessions
     */
    @Query("select max(s.sequenceNumber) from TableSession s where s.gameTable.id = :gameTableId")
    Integer findMaxSequenceNumber(@Param("gameTableId") String gameTableId);

    /**
     * Locks one session for the rest of the transaction. Correcting, holding and cancelling all read
     * the row, decide on its status and write it back, and two masters of the same table can do that
     * at the same time - the replacement of #194 in particular reads the table's highest sequence
     * number, which two concurrent cancellations would otherwise both read as the same value.
     *
     * @param id the session to lock
     * @return the session, locked, or empty if it does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TableSession s where s.id = :id")
    Optional<TableSession> findByIdForUpdate(@Param("id") String id);

    /**
     * Sessions whose date has gone by and that nobody closed, across several tables at once - the
     * third probe of the master dashboard (#136).
     *
     * @param gameTableIds the tables somebody runs. A table with nothing overdue is absent from the
     *                     result rather than reported as zero
     * @param status       the status that means "still open", always {@code Scheduled}
     * @param before       the cutoff, in UTC (#22) - in practice now
     * @return one row per table that has at least one overdue open session
     */
    @Query("""
            select new com.centraldungeon.tables.UnrecordedSessionCount(
                       s.gameTable.id, count(s), min(s.scheduledAt))
            from TableSession s
            where s.gameTable.id in :gameTableIds
              and s.status = :status
              and s.scheduledAt < :before
            group by s.gameTable.id
            """)
    List<UnrecordedSessionCount> countUnrecordedByTables(
            @Param("gameTableIds") Collection<String> gameTableIds,
            @Param("status") TableSessionStatus status,
            @Param("before") LocalDateTime before);
}
