package com.centraldungeon.approvals;

import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Something somebody asked for and an admin has to answer. Table {@code approval_requests}.
 *
 * <p>One table for every request that needs an approval (#42): the alternative was a table per flow,
 * each with its own screen and its own half of the same rules.
 *
 * <p><b>{@link #getEntityType()} and {@link #getEntityId()} are two loose columns and never a
 * {@code @ManyToOne}</b> (#78, #126). That is the whole price of the polymorphic reference and it is
 * paid in three places, not one:
 *
 * <ol>
 *   <li>{@link ApprovalService} validates the referenced entity exists <em>before</em> inserting -
 *       the database cannot, because there is no foreign key to check.</li>
 *   <li>Nothing here maps the reference as an association. A {@code @ManyToOne} would need one
 *       nullable column per target type, which is the shape #126 explicitly refused.</li>
 *   <li>{@link ApprovalOrphanCheckService} sweeps the unresolved rows and logs whatever no longer
 *       resolves. Without it the problem surfaces months later with no way to reconstruct what
 *       pointed at what - which is what this project already lived through with {@code table_files}
 *       pointing at deleted files.</li>
 * </ol>
 *
 * <p>The three associations that <b>are</b> real all point at {@code users} and all have a foreign
 * key: who asked, who reserved it (#100) and who resolved it.
 *
 * <p>No {@link com.centraldungeon.common.model.BaseEntity}: this table has no {@code updated_at}. A
 * request is created, optionally reserved, and resolved once - and each of those moments has a
 * column of its own that says more than a generic timestamp would.
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    /** UUID v7, assigned on persist - the same scheme as BaseEntity, which this one cannot extend. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** Which request this is. It decides what approving it does, and nothing else does. */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 32)
    private ApprovalRequestType requestType;

    /**
     * What kind of thing the request is about: {@code user} for the three types of F3.2,
     * {@code game_table} or {@code table_registration} for F3.4's. A plain column (#78) - see the
     * class comment for why it is not an association.
     */
    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    /** The id of that thing. Not a foreign key, which is the entire point and the entire cost (#126). */
    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    /** Who asked. A real foreign key - this one the database can keep. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    /** Why they are asking. Required: a request with no reason is one an admin cannot answer (#42). */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String justification;

    /** Where the request is. It leaves {@link ApprovalStatus#Pending} exactly once. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalStatus status = ApprovalStatus.Pending;

    /**
     * The admin who reserved this item from the shared queue (#100).
     *
     * <p>Mapped, never written here. The queue is F3.3 and so are the two endpoints that move this
     * column; F3.2 leaves it null and there is deliberately no method that sets it. It is mapped
     * anyway because the listing publishes {@code claimedByName}, and so that F3.3 adds behaviour
     * rather than a mapping.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by")
    private @Nullable User claimedBy;

    /** When it was reserved. Released automatically after a timeout - all of it F3.3 (#100). */
    @Column(name = "claimed_at")
    private @Nullable LocalDateTime claimedAt;

    /** The admin who answered. Null while the request is Pending. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private @Nullable User resolvedBy;

    /**
     * Why it was approved or rejected. Null only while Pending: #42 makes the reason mandatory at
     * <b>both</b> ends, and rejecting without one is half the mechanism.
     */
    @Column(name = "resolution_note", columnDefinition = "LONGTEXT")
    private @Nullable String resolutionNote;

    /** When it was answered. Null while Pending. */
    @Column(name = "resolved_at")
    private @Nullable LocalDateTime resolvedAt;

    /** When it was asked. Stamped on persist; it is what orders the queue, oldest first. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Required by JPA. */
    protected ApprovalRequest() {
    }

    /**
     * Opens a request, Pending and unreserved.
     *
     * <p>The reference is passed in already resolved: the caller is {@link ApprovalService}, which
     * checked that the entity exists before getting here (#78). This constructor cannot check it -
     * there is no foreign key and no association to fail on - which is precisely why the check has to
     * live in the service and be tested there.
     *
     * @param requestType   which request this is
     * @param entityType    what kind of thing it is about
     * @param entityId      the id of that thing, validated by the caller
     * @param requestedBy   who is asking
     * @param justification why, never blank
     */
    public ApprovalRequest(
            ApprovalRequestType requestType,
            String entityType,
            String entityId,
            User requestedBy,
            String justification) {
        this.requestType = requestType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.requestedBy = requestedBy;
        this.justification = justification;
    }

    /** Assigns the id and stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        createdAt = LocalDateTime.now();
    }

    /**
     * Answers the request, once and for all.
     *
     * <p>The four columns move together, which is why there is one method and not four setters: a
     * row that is {@code Approved} with no note, or that has a note and is still {@code Pending}, is
     * a state nothing in the application should be able to produce. Same reasoning as
     * {@code UserRole.revoke()} (#25).
     *
     * <p>It does not guard the transition. Refusing to re-resolve is a rule about the request and
     * about who is asking, so it lives in {@link ApprovalService} with its own error code and its own
     * test, next to every other rule of this slice.
     *
     * @param status         {@link ApprovalStatus#Approved} or {@link ApprovalStatus#Rejected}
     * @param resolvedBy     the admin answering, always the actor from the token (#121)
     * @param resolutionNote why, never blank - mandatory in both directions (#42)
     */
    public void resolve(ApprovalStatus status, User resolvedBy, String resolutionNote) {
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolutionNote = resolutionNote;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * Returns the request's id.
     *
     * @return the id
     * @throws IllegalStateException if called before the row was persisted
     */
    public String getId() {
        if (id == null) {
            throw new IllegalStateException("ApprovalRequest id is not assigned yet - it is set on persist");
        }
        return id;
    }

    /**
     * Returns which request this is.
     *
     * @return the type, never null
     */
    public ApprovalRequestType getRequestType() {
        return requestType;
    }

    /**
     * Returns what kind of thing the request is about.
     *
     * @return the entity type, never null. Resolved by {@link ApprovalEntityResolver}, never by JPA
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * Returns the id of what the request is about.
     *
     * @return the entity id, never null and never a foreign key (#78)
     */
    public String getEntityId() {
        return entityId;
    }

    /**
     * Returns who asked.
     *
     * @return the requester, lazily loaded
     */
    public User getRequestedBy() {
        return requestedBy;
    }

    /**
     * Returns why they are asking.
     *
     * @return the justification, never blank
     */
    public String getJustification() {
        return justification;
    }

    /**
     * Returns where the request is.
     *
     * @return the status, never null
     */
    public ApprovalStatus getStatus() {
        return status;
    }

    /**
     * Returns the admin who reserved this item (#100).
     *
     * @return the admin, or null - which is every row in F3.2, since nothing here writes it
     */
    public @Nullable User getClaimedBy() {
        return claimedBy;
    }

    /**
     * Returns when it was reserved (#100).
     *
     * @return the timestamp, or null - always null in F3.2
     */
    public @Nullable LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    /**
     * Returns the admin who answered.
     *
     * @return the resolver, lazily loaded, or null while Pending
     */
    public @Nullable User getResolvedBy() {
        return resolvedBy;
    }

    /**
     * Returns why it was approved or rejected.
     *
     * @return the note, or null while Pending
     */
    public @Nullable String getResolutionNote() {
        return resolutionNote;
    }

    /**
     * Returns when it was answered.
     *
     * @return the timestamp, or null while Pending
     */
    public @Nullable LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    /**
     * Returns when the request was made.
     *
     * @return the timestamp
     * @throws IllegalStateException if called before the row was persisted
     */
    public LocalDateTime getCreatedAt() {
        if (createdAt == null) {
            throw new IllegalStateException("ApprovalRequest createdAt is not assigned yet - it is set on persist");
        }
        return createdAt;
    }
}
