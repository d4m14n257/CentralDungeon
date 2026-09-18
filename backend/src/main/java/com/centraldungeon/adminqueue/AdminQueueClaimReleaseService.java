package com.centraldungeon.adminqueue;

import com.centraldungeon.approvals.ApprovalRequest;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalStatus;
import com.centraldungeon.common.config.AdminQueueProperties;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands back the reservations nobody finished (#100).
 *
 * <p><b>Without it the tray leaks.</b> An admin takes an item, closes the tab, and that piece of work
 * is invisible to every other admin for ever - the reservation that exists to stop two people doing
 * the same thing ends up stopping anybody from doing it at all. The timeout is what makes taking an
 * item a cheap, reversible act instead of a commitment, which is the only way a tray of this shape is
 * usable.
 *
 * <p><b>The first job of this application that runs on a short interval.</b> The other two
 * ({@code FileRetentionService}, {@code ApprovalOrphanCheckService}) are daily crons in the small
 * hours; this one runs every minute, because fifteen minutes is the promise and a sweep that ran once
 * a day would make it a day. The initial delay staggers it off application start so a restart does not
 * put it in the same instant as everything else that boots.
 *
 * <p>⚠️ <b>{@code @Transactional} has to be on the {@code @Scheduled} method too</b>, not only on the
 * one it calls. A call from there to {@link #releaseExpiredClaims()} is self-invocation and never goes
 * through the Spring proxy, so the annotation over there does nothing when the scheduler is the
 * caller - the trap both existing jobs document, and the one that fails <b>silently</b>: the entities
 * would come back detached, clearing their columns would be writes nobody is watching, and the job
 * would log a count having changed not one row.
 *
 * <p>⚠️ <b>The scheduler is per JVM</b> ({@code SchedulingConfig}). With more than one instance every
 * one of them runs this, which is harmless here for the reason that class names: releasing an already
 * released reservation leaves it released. Anything scheduled later that is not idempotent needs a
 * lock first.
 */
@Service
public class AdminQueueClaimReleaseService {

    private static final Logger log = LoggerFactory.getLogger(AdminQueueClaimReleaseService.class);

    /**
     * How many expired reservations one pass takes.
     *
     * <p>Bounded for the reason the other two jobs bound theirs: the pass is idempotent, so whatever
     * this leaves behind is taken by the next one sixty seconds later, and an unbounded load in one
     * transaction is how maintenance becomes an outage.
     */
    private static final int BATCH_SIZE = 200;

    /** The batch bound, as the repositories want it. */
    private static final Pageable BATCH = PageRequest.of(0, BATCH_SIZE);

    /** The {@code approval_requests} reservations. */
    private final ApprovalRequestRepository approvalRequestRepository;

    /** The {@code game_tables} reservations. */
    private final GameTableRepository gameTableRepository;

    /** Where the timeout comes from - configuration, so tightening it is not a deploy (#141 moves it to F3.5). */
    private final AdminQueueProperties adminQueueProperties;

    /**
     * @param approvalRequestRepository the {@code approval_requests} reservations
     * @param gameTableRepository       the {@code game_tables} reservations
     * @param adminQueueProperties      the timeout, from {@code app.admin-queue.claim-timeout}
     */
    public AdminQueueClaimReleaseService(
            ApprovalRequestRepository approvalRequestRepository,
            GameTableRepository gameTableRepository,
            AdminQueueProperties adminQueueProperties) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.gameTableRepository = gameTableRepository;
        this.adminQueueProperties = adminQueueProperties;
    }

    /**
     * Sweeps every minute.
     *
     * <p>{@code fixedDelay} and not {@code fixedRate}: the gap is measured from the end of one pass,
     * so a slow pass never overlaps the next one. See the class comment for why {@code @Transactional}
     * is repeated here.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void releaseTimedOutClaims() {
        int released = releaseExpiredClaims();
        if (released > 0) {
            log.info(
                    "Admin queue released {} reservation(s) held longer than {}",
                    released,
                    adminQueueProperties.claimTimeout());
        }
    }

    /**
     * Releases one batch of expired reservations.
     *
     * <p>Separate from the schedule so it can be called and asserted on directly - a test should not
     * have to wait sixty seconds to find out whether the rule is right, and the rule worth testing is
     * which reservations expire, not that Spring can read a {@code fixedDelay}.
     *
     * <p>Transactional in its own right for those direct callers; when the scheduled method calls it,
     * the outer transaction is already open and this simply joins it.
     *
     * <p>Only items still waiting on somebody are touched. A request that was answered, or a table
     * that was approved, keeps whatever {@code claimed_by} it had: it is in nobody's tray any more, so
     * clearing the column would be a write with no reader that also erases who was working on it.
     *
     * @return how many reservations this pass handed back. Zero is the normal answer on a platform
     *         where people finish what they start
     */
    @Transactional
    public int releaseExpiredClaims() {
        LocalDateTime cutoff = LocalDateTime.now().minus(adminQueueProperties.claimTimeout());

        List<ApprovalRequest> requests =
                approvalRequestRepository.findExpiredClaims(ApprovalStatus.Pending, cutoff, BATCH);
        for (ApprovalRequest request : requests) {
            request.releaseClaim();
        }

        List<GameTable> tables =
                gameTableRepository.findExpiredClaims(GameTableStatus.Preparation, cutoff, BATCH);
        for (GameTable table : tables) {
            table.releaseClaim();
        }

        return requests.size() + tables.size();
    }
}
