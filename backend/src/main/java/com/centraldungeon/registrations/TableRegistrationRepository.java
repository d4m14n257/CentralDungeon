package com.centraldungeon.registrations;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * Backs /my/tables: every game table where the actor holds an active Player registration.
     *
     * @param userId   the actor, from the token (#121)
     * @param status   the status to match, in practice Player
     * @param pageable page, size and sort
     * @return one page of their registrations in that status
     */
    Page<TableRegistration> findByUser_IdAndStatus(String userId, TableRegistrationStatus status, Pageable pageable);

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
