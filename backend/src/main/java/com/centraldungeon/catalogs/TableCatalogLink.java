package com.centraldungeon.catalogs;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * What the three bridge rows have in common. Only the composite key differs between them, so it
 * stays in each subclass; everything else is identical by definition.
 *
 * <p>The table id is a plain column and not a {@code @ManyToOne} to {@code GameTable}: catalogs
 * never navigate to a table, only the other way round, and mapping the association here would make
 * this package depend on {@code tables/} for nothing. The database still enforces the FK.
 */
@MappedSuperclass
public abstract class TableCatalogLink {

    /** Whether the table is currently tagged with the value, or was untagged and kept as a record. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TableCatalogLinkStatus status = TableCatalogLinkStatus.Used;

    /** When the link was made. Stamped on persist; the bridge tables have no {@code updated_at}. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Soft delete (#25): set when the link is removed. The row itself is never dropped. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Returns whether the link is live.
     *
     * @return the link's status, never null
     */
    public TableCatalogLinkStatus getStatus() {
        return status;
    }

    /**
     * Marks the link live or removed. A link is never deleted, only unlinked: the row is what says
     * the master once chose that alias (#56, #58).
     *
     * @param status the new status
     */
    public void setStatus(TableCatalogLinkStatus status) {
        this.status = status;
    }

    /**
     * Returns when the link was removed.
     *
     * @return the timestamp of the logical delete, or null while the link is live
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete of the link.
     *
     * @param deletedAt when it was removed, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
