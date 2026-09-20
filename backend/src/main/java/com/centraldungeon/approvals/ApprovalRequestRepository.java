package com.centraldungeon.approvals;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes {@code approval_requests}.
 *
 * <p>{@link JpaSpecificationExecutor} is what the admin search box needs: its predicate is only known
 * at runtime ({@link ApprovalSearchSpecification}), the same reason {@code UserRepository} has it.
 */
public interface ApprovalRequestRepository
        extends JpaRepository<ApprovalRequest, String>, JpaSpecificationExecutor<ApprovalRequest> {

    /**
     * One request, read <b>with its row locked</b> for the rest of the transaction.
     *
     * <p>What makes {@code REQUEST_ALREADY_RESOLVED} a rule instead of a hope. Reading the status
     * with {@link #findById} and then writing is a check-then-act with nothing in between: two admins
     * answering at the same moment each read {@code Pending} from their own snapshot, each pass the
     * guard, and the second UPDATE simply waits for the first one's row lock and overwrites it. Two
     * answers to one request - and when the two answers differ, a row that says {@code Rejected} over
     * somebody who is now a Master.
     *
     * <p><b>The locking read is also the status read, and that is the whole design</b> (#252). A lock
     * taken first and followed by a plain {@code findById} would be wrong in the way that is hardest
     * to see: under MySQL's REPEATABLE READ the waiting transaction wakes up and answers from the
     * snapshot it opened before it started waiting, cheerfully reading {@code Pending} on a row the
     * transaction it just waited for has already resolved. A locking read bypasses that snapshot and
     * sees the latest committed row, so it must be the first time this transaction touches the row -
     * a {@code findById} beforehand would put a stale instance in the persistence context and the
     * lock would refresh nothing.
     *
     * @param requestId the request to lock
     * @return the request, locked, or empty when no request has that id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ar from ApprovalRequest ar where ar.id = :requestId")
    Optional<ApprovalRequest> lockById(@Param("requestId") String requestId);

    /**
     * Whether this person already has an open request of this type.
     *
     * <p>The guard behind {@code REQUEST_ALREADY_PENDING}. Without it a double-clicked button fills
     * the queue with duplicates somebody has to resolve by hand, one by one.
     *
     * @param requestType the kind of request
     * @param userId      the requester
     * @param status      the status to look for - always {@link ApprovalStatus#Pending}
     * @return true when such a request is already open
     */
    boolean existsByRequestTypeAndRequestedBy_IdAndStatus(
            ApprovalRequestType requestType, String userId, ApprovalStatus status);

    /**
     * Whether an open request of this type already exists <b>about this entity</b>.
     *
     * <p>The same guard as the one above, asked the other way round, and F3.4 needed both. For the
     * three types of F3.2 the entity <em>is</em> the requester, so «one pending per type and per
     * person» said everything there was to say. The two types F3.4 added are about something else,
     * and asking by requester there refuses the second legitimate case: a master of two running
     * tables asking to pause the second one, and a {@code Secondary} asking to veto a second person
     * while the first request is still open. The duplicate worth stopping is «this table is already
     * waiting» and «somebody already asked about this person», which is this query.
     *
     * @param requestType the kind of request
     * @param entityId    what the request is about - a table, or an application
     * @param status      the status to look for - always {@link ApprovalStatus#Pending}
     * @return true when such a request is already open about that entity
     */
    boolean existsByRequestTypeAndEntityIdAndStatus(
            ApprovalRequestType requestType, String entityId, ApprovalStatus status);

    /**
     * The open requests of one type about one entity - what a direct action has to close out.
     *
     * <p>When a {@code Primary} presses the veto button themselves, any {@code PlayerBan} a
     * co-master had asked for on that same person has just been answered in fact, and leaving it
     * {@code Pending} would strand it: approving it afterwards answers
     * {@code REGISTRATION_ALREADY_BLOCKED} for ever, and the co-master who asked never learns what
     * happened.
     *
     * @param requestType the kind of request
     * @param entityId    what the requests are about
     * @param status      always {@link ApprovalStatus#Pending}
     * @return the open requests about that entity, oldest first
     */
    List<ApprovalRequest> findByRequestTypeAndEntityIdAndStatusOrderByCreatedAtAsc(
            ApprovalRequestType requestType, String entityId, ApprovalStatus status);

    /**
     * One bounded batch of requests nobody has answered yet, oldest first - what the orphan sweep
     * walks.
     *
     * <p>Only the unresolved ones. A resolved request is a record of something that happened, and
     * whether what it pointed at still exists years later says nothing anybody can act on; an open
     * one that points at nothing is a request an admin is about to be unable to answer.
     *
     * @param status   the status to sweep - always {@link ApprovalStatus#Pending}
     * @param pageable the batch bound. The sweep is read-only and idempotent, so a leftover tail is
     *                 picked up by the next pass
     * @return the batch, oldest first
     */
    @Query("select ar from ApprovalRequest ar where ar.status = :status order by ar.createdAt asc")
    List<ApprovalRequest> findUnresolved(@Param("status") ApprovalStatus status, Pageable pageable);

    /**
     * This source's contribution to the shared admin tray: the requests nobody has answered that are
     * either free or already the reader's (#100).
     *
     * <p><b>{@code claimed_by is null or claimed_by = :actorId} is the whole reservation rule seen
     * from the reading end</b> (modelo-datos.md §5): an item somebody took disappears from everybody
     * else's tray and stays in theirs, so two admins never start on the same thing. It is written in
     * the {@code WHERE} and not filtered afterwards, both because the index
     * {@code ix_ar_pending (status, claimed_by, created_at)} exists for exactly this question and
     * because a filter applied after a capped read would silently drop rows off the bottom.
     *
     * <p>Oldest first, which is the tray's order and not a caller's choice (#136), and bounded by the
     * caller: the tray merges its sources in memory, so each of them arrives with a ceiling.
     *
     * <p><b>Both people come with the row, and that is not an optimization detail.</b> The service
     * maps every fetched row into a line of the tray - all of them, before paging, because a page of
     * the merge is not a page of any one source - and each line publishes the requester's display
     * name. {@code requestedBy} is {@code LAZY} and every row names a <em>different</em> person, so
     * the first-level cache deduplicates nothing: without the fetch this is one extra SELECT per row,
     * up to the ceiling, to paint twenty. Multiplied by the tray's {@code refetchInterval} and by
     * every admin with the screen open, it is constant traffic for nothing. Same fix and same reason
     * as {@code MasterRepository.findByGameTablesAndType}, which fetches its users for the listing
     * that sits next to this one.
     *
     * <p>{@code claimedBy} is a {@code left join} because the column is null on most rows - an inner
     * one would drop exactly the unreserved items, which are the bulk of the tray.
     *
     * <p><b>It filters by type since F3.4</b>, and that is the point rather than a refinement. New
     * request types used to join the tray for free precisely because this query did not ask what
     * type a row was - which is right for four of the five and wrong for {@code PlayerBan}, whose
     * resolver is the table's {@code Primary} and not an admin (#39). A tray that listed it would
     * put work in front of admins that they are not allowed to do.
     *
     * @param status        the status that means "waiting", always {@link ApprovalStatus#Pending}
     * @param excludedTypes the types that are not the admins' to answer
     * @param actorId       the admin reading the tray, always from the token (#121)
     * @param pageable the per-source ceiling, never a page of the answer - the tray pages after the
     *                 merge, because a page of the merge is not a page of any one source
     * @return the requests waiting for this admin, oldest first, with both people already loaded
     */
    @Query("""
            select ar from ApprovalRequest ar
            join fetch ar.requestedBy
            left join fetch ar.claimedBy
            where ar.status = :status
              and ar.requestType not in :excludedTypes
              and (ar.claimedBy is null or ar.claimedBy.id = :actorId)
            order by ar.createdAt asc, ar.id asc
            """)
    List<ApprovalRequest> findQueueItems(
            @Param("status") ApprovalStatus status,
            @Param("excludedTypes") Collection<ApprovalRequestType> excludedTypes,
            @Param("actorId") String actorId,
            Pageable pageable);

    /**
     * The requests of one type, in one status, about a known set of entities.
     *
     * <p>What {@code GET /game-tables/{id}/ban-requests} reads (#39). The entity ids are resolved
     * first - the applications of that table - and handed in, because {@code entity_id} is a plain
     * column with no foreign key behind it (#78) and therefore nothing to join through. It is the
     * price of the polymorphic reference, paid where it is visible rather than hidden in a native
     * query.
     *
     * @param requestType which type, always {@link ApprovalRequestType#PlayerBan} today
     * @param status      which status, always {@link ApprovalStatus#Pending}
     * @param entityIds   the entities in scope; never empty - the caller short-circuits on empty
     * @return the matching requests, oldest first
     */
    List<ApprovalRequest> findByRequestTypeAndStatusAndEntityIdInOrderByCreatedAtAsc(
            ApprovalRequestType requestType, ApprovalStatus status, Collection<String> entityIds);

    /**
     * The reservations that went stale - what the release job hands back (#100).
     *
     * <p><b>Still-unresolved rows only.</b> A request that was answered keeps whatever
     * {@code claimed_by} it had as a record of who was working on it, and it is no longer in anybody's
     * tray, so clearing it would be a write with no reader that also erases a small piece of history.
     *
     * @param status   the status that means "still waiting", always {@link ApprovalStatus#Pending}
     * @param cutoff   reservations taken before this instant are expired
     * @param pageable the batch bound. The job is idempotent, so a leftover tail is taken by the next
     *                 pass a minute later
     * @return the expired reservations, oldest first
     */
    @Query("""
            select ar from ApprovalRequest ar
            where ar.status = :status
              and ar.claimedAt is not null
              and ar.claimedAt < :cutoff
            order by ar.claimedAt asc
            """)
    List<ApprovalRequest> findExpiredClaims(
            @Param("status") ApprovalStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);
}
