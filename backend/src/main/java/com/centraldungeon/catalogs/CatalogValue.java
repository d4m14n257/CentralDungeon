package com.centraldungeon.catalogs;

import com.centraldungeon.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * The shape the three catalogs share by definition, not by coincidence: systems, tags and platforms
 * are the same row with a different table name (arquitectura.md 2.4, case 2).
 *
 * <p><b>{@code canonicalId} is flat and always depth 1</b> (decisiones.md #59): an alias points
 * straight at its group's canonical entry, and a canonical entry has it {@code NULL}. There is no
 * tree, because under pure symmetric equivalence (#54) depth means nothing - the search resolves the
 * whole group from any member, and a second level would only be a cycle waiting to happen.
 *
 * <p>It is a plain column and not a {@code @ManyToOne} to itself: nothing ever needs to navigate to
 * the canonical entity from here, {@link AbstractCatalogService} always loads the group by id, and a
 * self-reference would drag lazy proxies into every read for no gain. The FK is still enforced by
 * the database (V1__baseline.sql).
 */
@MappedSuperclass
public abstract class CatalogValue extends BaseEntity {

    /**
     * The value as it is written and displayed - the alias its author chose, never rewritten to the
     * group's canonical entry (#58). Unique across the catalog, case-sensitively at the column level
     * and case-insensitively when a new value is proposed.
     */
    @Column(nullable = false, unique = true, length = 128)
    private String name;

    /** The group this value belongs to. NULL means this row is its group's canonical entry (#59). */
    @Column(name = "canonical_id", length = 64)
    private @Nullable String canonicalId;

    /** Where the value is in its lifecycle. Only {@code Accepted} shows to players and filters (#57). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CatalogStatus status = CatalogStatus.Created;

    /** Soft delete (#25, #81): set when an admin takes the value out of circulation, cleared on restore. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. Application code builds catalog values through their concrete subclass. */
    protected CatalogValue() {
    }

    /**
     * Builds a value that has not been reviewed yet: no group and status {@code Created} (#55).
     *
     * @param name the value as its author wrote it
     */
    protected CatalogValue(String name) {
        this.name = name;
    }

    /**
     * Returns the value as it is written and displayed.
     *
     * @return the name, never null on a persisted row
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the group this value belongs to.
     *
     * @return the canonical entry's id, or null when this row is the canonical entry itself (#59)
     */
    public @Nullable String getCanonicalId() {
        return canonicalId;
    }

    /**
     * Moves the value into a synonym group, or out of every group.
     *
     * <p>Only {@link AbstractCatalogService} calls this, and it validates the depth-1 invariant
     * first: the target has to be a canonical entry itself (#59).
     *
     * @param canonicalId the canonical entry to point at, or null to make this row canonical
     */
    public void setCanonicalId(@Nullable String canonicalId) {
        this.canonicalId = canonicalId;
    }

    /**
     * Returns where the value is in its lifecycle.
     *
     * @return the status, never null
     */
    public CatalogStatus getStatus() {
        return status;
    }

    /**
     * Moves the value through its lifecycle. Which transitions are legal is decided by
     * {@link AbstractCatalogService}, not here.
     *
     * @param status the new status
     */
    public void setStatus(CatalogStatus status) {
        this.status = status;
    }

    /**
     * Returns when the value was taken out of circulation.
     *
     * @return the timestamp of the logical delete, or null while the value is in circulation
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete. Never deletes the row, and never touches the links that
     * point at it - that is the whole point of #81.
     *
     * @param deletedAt when it was disabled, or null when it is being restored
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    /**
     * Tells whether this row is its group's canonical entry - the only legal target of a
     * {@code canonicalId} (#59).
     *
     * @return true when the value has no canonical entry above it
     */
    public boolean isCanonical() {
        return canonicalId == null;
    }
}
