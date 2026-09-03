package com.centraldungeon.tables;

import com.centraldungeon.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One session of a table: "the third Tuesday, at 23:00 UTC". Table {@code table_sessions}.
 *
 * <p><b>Sessions are materialized rows, not a calculation</b> (#33). Deriving them from
 * {@code start_date} + the weekly agenda + {@code total_sessions} would only work if nothing could
 * ever move them, and {@code Pause} moves them: resuming re-lays what was still pending, which
 * needs to know what was actually played and what was not. Materializing is also what makes it
 * possible to correct one date, cancel one session, and hang notes and comments off it (#31).
 *
 * <p><b>{@code scheduledAt} is UTC</b> (#22), like everything else that crosses this API. It starts
 * out on one of the table's agenda slots and stops being tied to it the moment a master corrects it:
 * a correction is about one evening, not about the week.
 *
 * <p>Unlike {@link TableSchedule}, the table is a {@code @ManyToOne} and not half of a composite key
 * - a session has an identity of its own, which is what a correction, a cancellation and an
 * attendance row all address.
 */
@Entity
@Table(name = "table_sessions")
public class TableSession extends BaseEntity {

    /** The table this session belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_table_id", nullable = false)
    private GameTable gameTable;

    /**
     * Which session of the run this is, from 1. Unique per table, and it never moves: a cancelled
     * session keeps its number as the record that it was planned, and the replacement of #194 takes
     * the next one.
     */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    /** When it happens, in UTC (#22). */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** Planned, played or called off. Only {@link TableSessionService} moves it. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TableSessionStatus status = TableSessionStatus.Scheduled;

    /** What the master wrote about this session. Plain text, theirs alone - it never reaches a player. */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String notes;

    /** Soft delete (#25), for when a session falls with its table. Null for a live row. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected TableSession() {
    }

    /**
     * Builds a planned session.
     *
     * @param gameTable      the table it belongs to
     * @param sequenceNumber which session of the run this is, from 1
     * @param scheduledAt    when it happens, in UTC (#22)
     */
    public TableSession(GameTable gameTable, int sequenceNumber, LocalDateTime scheduledAt) {
        this.gameTable = gameTable;
        this.sequenceNumber = sequenceNumber;
        this.scheduledAt = scheduledAt;
    }

    /**
     * Returns the table this session belongs to.
     *
     * @return the table, never null on a persisted row
     */
    public GameTable getGameTable() {
        return gameTable;
    }

    /**
     * Returns which session of the run this is.
     *
     * @return the sequence number, from 1
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns when the session happens.
     *
     * @return the instant, in UTC (#22)
     */
    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    /**
     * Moves the session to another instant - a master correcting one date, or the re-laying that
     * follows a resume (#33).
     *
     * @param scheduledAt the new instant, in UTC (#22)
     */
    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    /**
     * Returns whether the session is planned, played or called off.
     *
     * @return the status, never null
     */
    public TableSessionStatus getStatus() {
        return status;
    }

    /**
     * Marks what became of the session.
     *
     * <p>Which transitions are legal is {@link TableSessionService}'s to decide; calling this from
     * anywhere else skips that.
     *
     * @param status the new status
     */
    public void setStatus(TableSessionStatus status) {
        this.status = status;
    }

    /**
     * Returns what the master wrote about the session.
     *
     * @return the notes, or null when none were written
     */
    public @Nullable String getNotes() {
        return notes;
    }

    /**
     * Sets what the master wrote about the session.
     *
     * @param notes the notes, or null to clear them
     */
    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }

    /**
     * Returns when the session was logically deleted.
     *
     * @return the delete timestamp, or null for a live session
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete (#25).
     *
     * @param deletedAt when it was deleted, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
