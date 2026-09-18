package com.centraldungeon.approvals;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the requests that point at something which is no longer there (#78).
 *
 * <p><b>The third of the three things the polymorphic reference costs, and the one that is easiest to
 * skip.</b> {@code docs/fase-3-admin-owner.md} 7 says it outright: «dos cosas van con ella o no va: la
 * validación en el service antes de insertar, y el chequeo periódico de huérfanas. Sin la segunda, el
 * problema aparece meses después y sin forma de reconstruir qué apuntaba a qué». The validation in
 * {@link ApprovalService#submit} only covers the moment of writing; what it cannot cover is the
 * months afterwards, during which the thing the row points at can be deleted by some other feature
 * with no foreign key to stop it. This project already lived that with {@code table_files} pointing
 * at deleted files.
 *
 * <p><b>It logs and it does nothing else.</b> No deletion, no resolution, no status change. A row
 * that lost its anchor is the evidence of what happened, and deleting it automatically is precisely
 * how the ability to reconstruct anything is lost - which is the failure this job exists to prevent,
 * not one to automate. The log line names the request, its type and its reference, which is the
 * minimum somebody needs to go and look.
 *
 * <p>Only the <b>unresolved</b> rows are swept. Whether a two-year-old approved request still points
 * at a live table is a question nobody can act on; an open one pointing at nothing is a request an
 * admin is about to find unanswerable, because resolving it is refused with
 * {@code REQUEST_ENTITY_GONE}.
 */
@Service
public class ApprovalOrphanCheckService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalOrphanCheckService.class);

    /**
     * How many requests one pass looks at.
     *
     * <p>Bounded for the reason {@code FileRetentionService} bounds its own: the job is read-only and
     * idempotent, so whatever the bound leaves behind is picked up by the next pass, and loading an
     * unbounded set into one transaction is how maintenance becomes an outage. There should never be
     * hundreds of pending requests anyway - if there are, the log saying so is itself worth having.
     */
    private static final int BATCH_SIZE = 200;

    /** The {@code approval_requests} rows, read oldest first. */
    private final ApprovalRequestRepository approvalRequestRepository;

    /** Answers "does this still exist?" for each entity type (#78). */
    private final ApprovalEntityResolver entityResolver;

    /**
     * @param approvalRequestRepository the {@code approval_requests} rows
     * @param entityResolver            resolves the polymorphic reference
     */
    public ApprovalOrphanCheckService(
            ApprovalRequestRepository approvalRequestRepository, ApprovalEntityResolver entityResolver) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.entityResolver = entityResolver;
    }

    /**
     * Sweeps once a day, in the small hours.
     *
     * <p>05:30 UTC, half an hour after {@code FileRetentionService}: the community plays at night in
     * the Americas, which is early morning of the next day in UTC (#22), and staggering the two keeps
     * them from competing for the same quiet window.
     *
     * <p>⚠️ <b>{@code @Transactional} has to be on <em>this</em> method too</b>, not only on the one
     * it calls. A call from here to {@link #reportOrphans()} is self-invocation and never goes
     * through the Spring proxy, so the annotation over there does nothing when the scheduler is the
     * caller - the same trap {@code FileRetentionService} documents.
     *
     * <p>What it buys here is <b>one snapshot for the whole pass</b>. Without it, the batch read and
     * every existence check after it run in transactions of their own, so the sweep judges rows it
     * read at one moment against a database it re-reads at a dozen later ones. This job only reads,
     * so nothing is corrupted either way - but a report is only worth reading if it describes a
     * single coherent instant.
     */
    @Scheduled(cron = "0 30 5 * * *")
    @Transactional(readOnly = true)
    public void checkForOrphanedReferences() {
        int orphans = reportOrphans();
        if (orphans > 0) {
            log.warn(
                    "Orphan check found {} unresolved approval request(s) pointing at entities that no longer exist",
                    orphans);
        }
    }

    /**
     * Looks at one batch of unresolved requests and logs the ones whose reference no longer resolves.
     *
     * <p>Separate from the schedule so it can be called and asserted on directly - a test should not
     * have to wait for a cron expression to come round, and the rule worth testing is which rows get
     * reported, not that Spring can read a crontab.
     *
     * @return how many orphans this pass found. Zero is the normal answer, and the one a healthy
     *         platform gives every day
     */
    @Transactional(readOnly = true)
    public int reportOrphans() {
        List<ApprovalRequest> pending =
                approvalRequestRepository.findUnresolved(ApprovalStatus.Pending, PageRequest.of(0, BATCH_SIZE));

        int orphans = 0;
        for (ApprovalRequest request : pending) {
            if (isOrphaned(request)) {
                orphans++;
                // Everything needed to go and look, on one line: which request, who asked, what they
                // asked for, and what it pointed at. Reconstructing that after the fact is exactly
                // what #78 warns is impossible once the reference is gone and nothing wrote it down -
                // and the requester is the half that makes the line actionable without a query.
                log.warn(
                        "Orphaned approval request {}: type={} requested by {} points at {}:{} which no longer exists",
                        request.getId(),
                        request.getRequestType().wireName(),
                        request.getRequestedBy().getId(),
                        request.getEntityType(),
                        request.getEntityId());
            }
        }
        return orphans;
    }

    /**
     * Asks the resolver about one row, and survives the answer being an exception.
     *
     * <p>{@link ApprovalEntityResolver#exists} throws on an entity type it has no case for, which is
     * the right answer to a flow somebody added without teaching it - the fault is named instead of
     * being disguised as a missing entity. But a sweep is a report over many rows, and one
     * misconfigured row must not cost the report on all the others: the pass would die at the first
     * one and every genuine orphan behind it would stay unreported, which is the failure this job
     * exists to prevent, arriving by a different door.
     *
     * <p>So it is caught here, logged at {@code ERROR} - louder than an orphan, because an orphan is
     * data going wrong and this is code being incomplete - and the row is not counted as an orphan.
     * It is not one: nobody knows what it is, which is precisely what the line says.
     */
    private boolean isOrphaned(ApprovalRequest request) {
        try {
            return !entityResolver.exists(request.getEntityType(), request.getEntityId());
        } catch (IllegalStateException unknownType) {
            log.error(
                    "Cannot check approval request {}: entity type '{}' has no resolver case - "
                            + "a request flow was added without teaching ApprovalEntityResolver about it",
                    request.getId(),
                    request.getEntityType(),
                    unknownType);
            return false;
        }
    }
}
