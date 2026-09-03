package com.centraldungeon.tables;

import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Someone who runs a table. Table {@code masters}.
 *
 * <p><b>This row is what authorizes running a table, not the {@code Master} role</b> (#135). Holding
 * the role means "I can create tables of my own" and nothing else; every read and every mutation of
 * a concrete table checks for a row here instead (#121).
 *
 * <p>Exactly one live {@code Primary} per table (#73). MySQL has no partial unique index, so the
 * invariant is held by {@link MasterService} behind a pessimistic lock, and covered by
 * {@code MasterServiceIT}.
 *
 * <p>Careful with the word Owner: {@code MasterType} is about one table, {@code PlatformRole.OWNER}
 * is about the whole platform, and they are unrelated (#67, #71, #89).
 */
@Entity
@Table(name = "masters")
public class Master {

    /** The pair (table, user) this row joins. */
    @EmbeddedId
    private MasterId id;

    /** The table being run. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("gameTableId")
    @JoinColumn(name = "game_table_id")
    private GameTable gameTable;

    /** The person running it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    /** Primary or Secondary (#71). On screen these are "master" and "co-master", never these words (#166). */
    @Enumerated(EnumType.STRING)
    @Column(name = "master_type", nullable = false, length = 32)
    private MasterType masterType;

    /** Live or logically deleted. A master row is never dropped, only marked. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MasterRowStatus status = MasterRowStatus.Created;

    /** When the person was made a master of this table. Stamped on persist. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** When the row was logically deleted, or null while it is live. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected Master() {
    }

    /**
     * Makes someone a master of a table.
     *
     * @param gameTable  the table, already persisted - its id becomes half the composite key
     * @param user       the person to put in charge
     * @param masterType Primary or Secondary. Whether a second Primary is legal is
     *                   {@link MasterService}'s call, not this constructor's
     */
    public Master(GameTable gameTable, User user, MasterType masterType) {
        this.gameTable = gameTable;
        this.user = user;
        this.masterType = masterType;
        this.id = new MasterId(gameTable.getId(), user.getId());
    }

    /** Stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Returns the table being run.
     *
     * @return the table, lazily loaded
     */
    public GameTable getGameTable() {
        return gameTable;
    }

    /**
     * Returns the person running it.
     *
     * @return the user, lazily loaded
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns whether this is the table's Primary or one of its Secondaries.
     *
     * @return the master type, never null
     */
    public MasterType getMasterType() {
        return masterType;
    }

    /**
     * Promotes or demotes this master.
     *
     * @param masterType the new type. Keeping exactly one live Primary is {@link MasterService}'s
     *                   responsibility (#73) - this setter enforces nothing
     */
    public void setMasterType(MasterType masterType) {
        this.masterType = masterType;
    }

    /**
     * Returns whether the row is live.
     *
     * @return the row's status, never null
     */
    public MasterRowStatus getStatus() {
        return status;
    }

    /**
     * Marks the row live or deleted. Only the cascade of a deleted table writes this (#25, #175); a
     * master row is never dropped.
     *
     * @param status the new row status
     */
    public void setStatus(MasterRowStatus status) {
        this.status = status;
    }

    /**
     * Stamps or clears the logical delete. Written by the same cascade as {@link #setStatus}.
     *
     * @param deletedAt when the row was deleted, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
