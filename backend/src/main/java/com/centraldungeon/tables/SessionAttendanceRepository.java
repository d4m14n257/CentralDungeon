package com.centraldungeon.tables;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes {@code session_attendance} - who was at which session (#36). */
public interface SessionAttendanceRepository extends JpaRepository<SessionAttendance, SessionAttendanceId> {

    /**
     * The attendance rows of a whole calendar, in one read.
     *
     * <p>The sessions tab shows every session with its roster, and asking per session would be
     * twelve queries for one screen - the N+1 that #137 names as the real risk.
     *
     * @param sessionIds the sessions on screen
     * @return every attendance row of those sessions
     */
    List<SessionAttendance> findById_TableSessionIdIn(Collection<String> sessionIds);

    /**
     * One person's attendance rows across a table, whatever it says.
     *
     * @param gameTableId the table
     * @param userId      the player, always the actor from the token (#121)
     * @return their rows on that table's sessions
     */
    @Query("select a from SessionAttendance a where a.session.gameTable.id = :gameTableId and a.id.userId = :userId")
    List<SessionAttendance> findByTableAndUser(@Param("gameTableId") String gameTableId, @Param("userId") String userId);

    /**
     * The historical attendance of #137: the three counts, grouped, with {@code Unknown} left out of
     * the denominator.
     *
     * <p>Excluding {@code Unknown} is not a detail. A table of twelve sessions that started yesterday
     * has eleven unrecorded ones, and counting those would make every player read as a chronic
     * absentee exactly while a master is judging them.
     *
     * <p>Nothing here is cached. The precedent is #11, which removed a trigger-maintained counter to
     * kill that class of inconsistency at the root; the fallback if it ever needs caching is #97's,
     * a projection column with explicit recalculation triggers.
     *
     * @param gameTableId the table the count is scoped to
     * @param userId      the player, always the actor from the token (#121)
     * @return one row per value actually recorded, {@code Unknown} never among them
     */
    @Query("select new com.centraldungeon.tables.AttendanceCount(a.attendance, count(a)) from SessionAttendance a "
            + "where a.session.gameTable.id = :gameTableId and a.id.userId = :userId "
            + "and a.attendance <> com.centraldungeon.tables.AttendanceStatus.Unknown "
            + "group by a.attendance")
    List<AttendanceCount> countByTableAndUser(@Param("gameTableId") String gameTableId, @Param("userId") String userId);

    /**
     * {@link #countByTableAndUser}'s sibling with no game table to scope it to - the aggregate
     * attendance a profile shows (modelo-datos.md §5). Same grouped query, same reason {@code Unknown}
     * is left out, just asked across every table the person has ever played instead of one.
     *
     * @param userId the person, whoever's profile is being read
     * @return one row per value actually recorded, {@code Unknown} never among them
     */
    @Query("select new com.centraldungeon.tables.AttendanceCount(a.attendance, count(a)) from SessionAttendance a "
            + "where a.id.userId = :userId "
            + "and a.attendance <> com.centraldungeon.tables.AttendanceStatus.Unknown "
            + "group by a.attendance")
    List<AttendanceCount> countByUser(@Param("userId") String userId);

    /**
     * {@link #countByTableAndUser}'s batched sibling: the same three counts, one row per (table,
     * value) pair, for a whole page of tables in a single query - the history screen's own N+1 risk
     * (#133a) and the exact shape {@code CatalogUsageCount} and
     * {@code FileService.usagesByFileId} already hold this line for a page of catalog values and a
     * page of files.
     *
     * @param gameTableIds the tables on the page
     * @param userId       the player, always the actor from the token (#121)
     * @return one row per (table, value) actually recorded, {@code Unknown} never among them; a
     *         table with nothing recorded contributes no rows at all
     */
    @Query("select new com.centraldungeon.tables.TableAttendanceCount(a.session.gameTable.id, a.attendance, count(a)) "
            + "from SessionAttendance a "
            + "where a.session.gameTable.id in :gameTableIds and a.id.userId = :userId "
            + "and a.attendance <> com.centraldungeon.tables.AttendanceStatus.Unknown "
            + "group by a.session.gameTable.id, a.attendance")
    List<TableAttendanceCount> countByGameTablesAndUser(
            @Param("gameTableIds") Collection<String> gameTableIds, @Param("userId") String userId);
}
