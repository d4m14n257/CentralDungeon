package com.centraldungeon.registrations;

import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes {@code table_registrations} - who applied to what, and how it went. */
public interface TableRegistrationRepository extends JpaRepository<TableRegistration, String> {

    /**
     * The one-active-registration-per-pair rule (#28): somebody cannot apply twice to the same
     * table while an application is still alive. Called under the table's pessimistic lock, because
     * "no row exists yet" has no row of its own to lock.
     *
     * @param gameTableId the table
     * @param userId      the applicant, from the token
     * @param statuses    which statuses count as active - Candidate and Player
     * @return true when an active registration already exists
     */
    boolean existsByGameTable_IdAndUser_IdAndStatusIn(String gameTableId, String userId, Collection<TableRegistrationStatus> statuses);

    /**
     * How many people hold a given status on a table. With {@code Player} it is the seat count the
     * max_players cap is checked against (#34), and the "3/5" every card shows.
     *
     * @param gameTableId the table
     * @param status      the status to count
     * @return how many registrations are in it
     */
    long countByGameTable_IdAndStatus(String gameTableId, TableRegistrationStatus status);

    /**
     * A table's applicants or its players, paginated - the candidates tab and the players tab.
     *
     * @param gameTableId the table
     * @param status      Candidate or Player, depending on the tab
     * @param pageable    page, size and sort
     * @return one page of its registrations
     */
    Page<TableRegistration> findByGameTable_IdAndStatus(String gameTableId, TableRegistrationStatus status, Pageable pageable);

    /**
     * A table's registrations in the order they arrived. FIFO is not negotiable
     * (modelo-datos.md #28): callers never re-sort this, because it is what decides who gets
     * auto-rejected when the table fills up (#34).
     *
     * @param gameTableId the table
     * @param status      the status to list, in practice Candidate
     * @return its registrations, oldest first
     */
    List<TableRegistration> findByGameTable_IdAndStatusOrderByCreatedAtAsc(String gameTableId, TableRegistrationStatus status);

    /**
     * Backs /my/applications: everything the actor ever applied to, whatever came of it.
     *
     * @param userId   the actor, from the token (#121)
     * @param pageable page, size and sort
     * @return one page of their applications
     */
    Page<TableRegistration> findByUser_Id(String userId, Pageable pageable);

    /**
     * The same listing, minus the soft-deleted rows. A withdrawn application (#178) and one that
     * fell with its table (#175) are both {@code Deleted}, and neither is something the applicant
     * should keep reading about on /my/applications.
     *
     * @param userId   the actor, from the token (#121)
     * @param status   the status to leave out, in practice Deleted
     * @param pageable page, size and sort
     * @return one page of their applications, the marked ones excluded
     */
    Page<TableRegistration> findByUser_IdAndStatusNot(String userId, TableRegistrationStatus status, Pageable pageable);

    /**
     * Backs /my/tables: every game table where the actor holds an active Player registration.
     *
     * @param userId   the actor, from the token (#121)
     * @param status   the status to match, in practice Player
     * @param pageable page, size and sort
     * @return one page of their registrations in that status
     */
    Page<TableRegistration> findByUser_IdAndStatus(String userId, TableRegistrationStatus status, Pageable pageable);

    /**
     * The tables somebody plays at, in the statuses that count as a live commitment - the other half
     * of what the clash rules of #178 compare against. Playing and running weigh the same there:
     * they are the same person in the same stretch of the week.
     *
     * @param userId   the person whose commitments are being collected, always from the token (#121)
     * @param statuses the table statuses that count as committed. {@code Pause} is not among them,
     *                 because a paused table does not reserve the slot (#32, #178)
     * @return the tables where they hold a Player registration and the table is in one of those
     *         statuses
     */
    @Query("select r.gameTable from TableRegistration r where r.user.id = :userId "
            + "and r.status = com.centraldungeon.registrations.TableRegistrationStatus.Player "
            + "and r.gameTable.status in :statuses")
    List<GameTable> findTablesPlayedByUserInStatuses(
            @Param("userId") String userId, @Param("statuses") Collection<GameTableStatus> statuses);

    /**
     * Somebody's applications in one status, unpaginated. Used for the notice R4 sends: when a
     * person is accepted somewhere, their other pending applications are checked for a clash and
     * they are told about it (#178).
     *
     * @param userId the applicant
     * @param status the status to match, in practice Candidate
     * @return their registrations in that status
     */
    List<TableRegistration> findByUser_IdAndStatus(String userId, TableRegistrationStatus status);

    /**
     * Whether anybody is involved with a table yet. It is what separates a table that can still be
     * deleted from one that can only be cancelled (#175): once someone applied, there is history.
     *
     * @param gameTableId the table
     * @param statuses    which statuses count as involvement
     * @return true when at least one registration is in one of them
     */
    boolean existsByGameTable_IdAndStatusIn(String gameTableId, Collection<TableRegistrationStatus> statuses);

    /**
     * Every registration of a table, whatever its status - what the delete cascade walks (#25, #175).
     *
     * @param gameTableId the table
     * @return all of its registrations
     */
    List<TableRegistration> findByGameTable_Id(String gameTableId);
}
