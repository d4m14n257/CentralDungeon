package com.centraldungeon.approvals;

import jakarta.persistence.LockModeType;
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
}
