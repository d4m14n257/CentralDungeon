package com.centraldungeon.tables;

import com.centraldungeon.common.model.BaseEntity;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * A game table: the central aggregate of the system. Table {@code game_tables}.
 *
 * <p>Named GameTable and not Table because {@code jakarta.persistence.Table} owns that name in every
 * file that maps an entity. Same reason {@code GameSystem} is not {@code System}.
 *
 * <p>Its lifecycle is a nine-state machine driven entirely by {@link GameTableService}, never by a
 * setter called from outside it (decisiones.md, ciclo de vida de la mesa). Who runs the table is
 * <b>not</b> a column here: it is a row in {@code masters} (#135), and who plays in it is a row in
 * {@code table_registrations}.
 *
 * <p>{@code claimed_by} / {@code claimed_at} are the admin queue's reservation (#100). They arrived
 * with F3.3 and needed <b>no migration</b>: {@code V1__baseline.sql} declared both columns and the
 * foreign key from the start, exactly as the note that used to sit here promised (modelo-datos.md 4).
 * Only {@link com.centraldungeon.adminqueue.AdminQueueService} moves them - a table is reserved from
 * the shared tray and from nowhere else.
 */
@Entity
@Table(name = "game_tables")
public class GameTable extends BaseEntity {

    /** How the table is run - "Public", "First class". Null while nobody has chosen one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_type_id")
    private @Nullable TableType tableType;

    /** The table's title, what the explorer lists it by. */
    @Column(nullable = false, length = 128)
    private String name;

    /** What the table is about. Rich text, sanitized on write and on read (#62). */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String description;

    /**
     * What is allowed at this table - the house rules a master writes out so nobody has to guess.
     * Rich text, same sanitization as the description (#62).
     */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String permitted;

    /** What is asked of a player to be accepted. Rich text, same sanitization as the description. */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String requirements;

    /**
     * The day the table starts running - the cut-off the calendar is laid out from (#26, #230).
     *
     * <p><b>A date and not an instant</b>: no session ever takes its hour from here. The hours come
     * from the weekly agenda, and this only says which week the first one falls in. A plain date is
     * also the same day in every zone, which an instant is not (#22, #230).
     */
    @Column(name = "start_date")
    private @Nullable LocalDate startDate;

    /** How long <b>one</b> session lasts - not the campaign. Stored as a time of day, read as a length. */

    /** How many sessions are planned (#26). With the start date and the schedule, it is the whole agenda. */
    @Column(name = "total_sessions")
    private @Nullable Integer totalSessions;

    /** The player cap (#24). Null means the master has not set one. */
    @Column(name = "max_players")
    private @Nullable Integer maxPlayers;

    /** Where the table is in its nine-state lifecycle. Only GameTableService moves it. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameTableStatus status = GameTableStatus.Draft;

    /**
     * When the table stopped being an ongoing thing: stamped on entering {@code Finished} or
     * {@code Canceled}, and never again (#180).
     *
     * <p>It is not decoration. It starts the two-week window in which the people who shared the
     * table can still see each other's profiles (#44), and it is what tells a table closed yesterday
     * from one closed a year ago.
     */
    @Column(name = "closed_at")
    private @Nullable LocalDateTime closedAt;

    /** Soft delete (#25). Only set for a table that was never public (#175); anything past that is cancelled. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /**
     * Who created the row - a master creating their own table, or an admin creating an unassigned
     * one (#72). <b>Not</b> the same as who runs it: that is a row in {@code masters} (#135).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * The admin who reserved this table's review from the shared tray (#100).
     *
     * <p>Null for every table nobody is looking at right now, which is the normal state. It is not
     * part of the lifecycle: reserving a table changes who is working on it, never where it is.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by")
    private @Nullable User claimedBy;

    /**
     * When it was reserved. Released automatically after
     * {@code app.admin-queue.claim-timeout} (#100).
     */
    @Column(name = "claimed_at")
    private @Nullable LocalDateTime claimedAt;

    /** Required by JPA. */
    protected GameTable() {
    }

    /**
     * Builds a table in {@code Draft}, with nothing but its title and its author (#245): it is its
     * master's alone until they send it to review.
     *
     * @param name      the table's title
     * @param createdBy who is creating it
     */
    public GameTable(String name, User createdBy) {
        this.name = name;
        this.createdBy = createdBy;
    }

    /**
     * Returns how the table is run.
     *
     * @return the table type, or null when none was chosen
     */
    public @Nullable TableType getTableType() {
        return tableType;
    }

    /**
     * Sets how the table is run.
     *
     * @param tableType the type, or null to clear it
     */
    public void setTableType(@Nullable TableType tableType) {
        this.tableType = tableType;
    }

    /**
     * Returns the table's title.
     *
     * @return the name, never null on a persisted row
     */
    public String getName() {
        return name;
    }

    /**
     * Renames the table.
     *
     * @param name the new title. A table is still being written while its master may edit it, and
     *             the title is part of what they are writing
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns what the table is about.
     *
     * @return the description as rich text, or null when there is none
     */
    public @Nullable String getDescription() {
        return description;
    }

    /**
     * Sets what the table is about.
     *
     * @param description rich text, already sanitized by the service (#62)
     */
    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    /**
     * Returns the table's house rules.
     *
     * @return what is allowed at the table, as rich text, or null when nothing was written
     */
    public @Nullable String getPermitted() {
        return permitted;
    }

    /**
     * Sets the table's house rules.
     *
     * @param permitted rich text, already sanitized by the service (#62)
     */
    public void setPermitted(@Nullable String permitted) {
        this.permitted = permitted;
    }

    /**
     * Returns what is asked of a player to be accepted.
     *
     * @return the requirements as rich text, or null when there are none
     */
    public @Nullable String getRequirements() {
        return requirements;
    }

    /**
     * Sets what is asked of a player to be accepted.
     *
     * @param requirements rich text, already sanitized by the service (#62)
     */
    public void setRequirements(@Nullable String requirements) {
        this.requirements = requirements;
    }

    /**
     * Returns the day the table starts running.
     *
     * @return the day, or null while it is undecided
     */
    public @Nullable LocalDate getStartDate() {
        return startDate;
    }

    /**
     * Sets the day the table starts running.
     *
     * @param startDate the day, with no hour and no zone attached (#230)
     */
    public void setStartDate(@Nullable LocalDate startDate) {
        this.startDate = startDate;
    }



    /**
     * Returns how many sessions are planned.
     *
     * @return the planned count (#26), or null when it is not set
     */
    public @Nullable Integer getTotalSessions() {
        return totalSessions;
    }

    /**
     * Sets how many sessions are planned.
     *
     * @param totalSessions the planned count; it decides how many rows are materialized when the
     *                      table opens (#26, #33)
     */
    public void setTotalSessions(@Nullable Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    /**
     * Returns the player cap.
     *
     * @return the maximum number of players (#24), or null when there is no cap
     */
    public @Nullable Integer getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Sets the player cap.
     *
     * @param maxPlayers the maximum number of players, or null for no cap
     */
    public void setMaxPlayers(@Nullable Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    /**
     * Returns where the table is in its lifecycle.
     *
     * @return the status, never null
     */
    public GameTableStatus getStatus() {
        return status;
    }

    /**
     * Returns when the table was closed.
     *
     * @return the instant it entered {@code Finished} or {@code Canceled} (#180), or null while it
     *         is still going
     */
    public @Nullable LocalDateTime getClosedAt() {
        return closedAt;
    }

    /**
     * Stamps when the table closed.
     *
     * <p>Only {@link GameTableService} calls this, on the two transitions that end a table's life,
     * and it never overwrites a stamp that is already there: a table closes once (#44, #180).
     *
     * @param closedAt the closing instant, in UTC
     */
    public void setClosedAt(@Nullable LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    /**
     * Returns when the table was logically deleted.
     *
     * @return the delete timestamp, or null for a live table
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete (#25).
     *
     * @param deletedAt when it was deleted, or null to bring it back. Only a table that was never
     *                  public gets here at all (#175)
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    /**
     * Moves the table through its lifecycle.
     *
     * <p>Which transitions are legal, who may make them and what history they leave is decided by
     * {@link GameTableService}. Calling this from anywhere else skips all of that.
     *
     * @param status the new status
     */
    public void setStatus(GameTableStatus status) {
        this.status = status;
    }

    /**
     * Returns who created the row.
     *
     * @return the author. Not necessarily a master of the table - an admin can create one without
     *         running it (#72)
     */
    public User getCreatedBy() {
        return createdBy;
    }

    /**
     * Returns the admin who reserved this table's review (#100).
     *
     * @return the admin, or null when nobody has taken it
     */
    public @Nullable User getClaimedBy() {
        return claimedBy;
    }

    /**
     * Returns when the reservation was taken.
     *
     * @return the timestamp, or null when the table is not reserved
     */
    public @Nullable LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    /**
     * Takes the reservation for an admin.
     *
     * <p>Two columns and one method, the same reasoning {@link com.centraldungeon.approvals
     * .ApprovalRequest#resolve} gives for its four: a row that names an admin with no instant, or an
     * instant with no admin, is a state nothing should be able to produce. Whether the reservation is
     * allowed - free, or already this same admin's - is decided by
     * {@link com.centraldungeon.adminqueue.AdminQueueService} under the table's row lock, because it
     * is a rule about two admins racing and not about one row.
     *
     * @param admin     the admin taking it, always the actor from the token (#121)
     * @param claimedAt when they took it
     */
    public void claim(User admin, LocalDateTime claimedAt) {
        this.claimedBy = admin;
        this.claimedAt = claimedAt;
    }

    /**
     * Gives the reservation back: by the admin who had it, or by the job that expires it after
     * {@code app.admin-queue.claim-timeout} (#100).
     */
    public void releaseClaim() {
        this.claimedBy = null;
        this.claimedAt = null;
    }
}
