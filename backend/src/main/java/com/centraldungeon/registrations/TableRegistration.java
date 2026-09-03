package com.centraldungeon.registrations;

import com.centraldungeon.common.model.BaseEntity;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * Somebody's application to play at a table, and what became of it. Table
 * {@code table_registrations}.
 *
 * <p>It is the other half of membership: {@code masters} says who runs a table, this says who plays
 * at it, and the two sets are disjoint (#154). Only one may be alive per (table, person) pair at a
 * time (#28), an invariant {@code RegistrationService} holds under the table's lock because there
 * is no row to lock for "nothing exists yet".
 */
@Entity
@Table(name = "table_registrations")
public class TableRegistration extends BaseEntity {

    /** The table applied to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_table_id", nullable = false)
    private GameTable gameTable;

    /** The applicant. Always the actor from the token, never an id from the URL (#121). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Where the application stands. Born a Candidate: applying is the act, accepting is a master's. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TableRegistrationStatus status = TableRegistrationStatus.Candidate;

    /** The applicant's note to the master. Optional - karma and profile already say a lot. */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String description;

    /** Required by JPA. */
    protected TableRegistration() {
    }

    /**
     * Records an application, as a Candidate.
     *
     * @param gameTable   the table applied to
     * @param user        the applicant, from the token
     * @param description their note to the master, or null
     */
    public TableRegistration(GameTable gameTable, User user, @Nullable String description) {
        this.gameTable = gameTable;
        this.user = user;
        this.description = description;
    }

    /**
     * Returns the table applied to.
     *
     * @return the table, lazily loaded
     */
    public GameTable getGameTable() {
        return gameTable;
    }

    /**
     * Returns the applicant.
     *
     * @return the user, lazily loaded
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns where the application stands.
     *
     * @return the status, never null
     */
    public TableRegistrationStatus getStatus() {
        return status;
    }

    /**
     * Moves the application forward.
     *
     * @param status the new status. Which transitions are legal, who may make them and what cap
     *               they run into is {@code RegistrationService}'s call (#28, #34)
     */
    public void setStatus(TableRegistrationStatus status) {
        this.status = status;
    }

    /**
     * Returns the applicant's note.
     *
     * @return the note, or null when they wrote none
     */
    public @Nullable String getDescription() {
        return description;
    }
}
