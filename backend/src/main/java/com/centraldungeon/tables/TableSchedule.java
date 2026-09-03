package com.centraldungeon.tables;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * One slot of a table's weekly agenda: "Tuesday at 20:00, every week". Table
 * {@code table_schedules}.
 *
 * <p><b>Everything here is UTC</b> (#22). The conversion to and from the reader's own time happens
 * once, in the frontend; a slot the players think of as Tuesday night in America is stored as
 * Wednesday in the small hours, and nothing on this side of the API pretends otherwise.
 *
 * <p>The slot carries no duration of its own. How long a session lasts is
 * {@link GameTable#getDuration()}, a property of the table, and it is what turns this row into the
 * interval the clash rules of #178 compare - see {@link WeeklyInterval}.
 *
 * <p>No {@code @ManyToOne} to the table: the id is half the composite key already, and the agenda is
 * always read for a table whose id the caller is holding. The database still enforces the FK.
 */
@Entity
@Table(name = "table_schedules")
public class TableSchedule {

    /** The triple (table, weekday, hourtime) this slot occupies. */
    @EmbeddedId
    private TableScheduleId id;

    /** Whether the slot is still part of the agenda. Rows are marked, never dropped - see the enum. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TableScheduleStatus status = TableScheduleStatus.Created;

    /** When the slot was taken out of the agenda, or null while it is live. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected TableSchedule() {
    }

    /**
     * Adds a slot to a table's agenda, live from the start.
     *
     * @param gameTableId the table
     * @param weekday     the day of the week, in UTC (#22)
     * @param hourtime    the time of day it starts, in UTC and truncated to the minute
     */
    public TableSchedule(String gameTableId, Weekday weekday, LocalTime hourtime) {
        this.id = new TableScheduleId(gameTableId, weekday, hourtime);
    }

    /**
     * Returns the triple this slot occupies.
     *
     * @return the composite key, never null on a persisted row
     */
    public TableScheduleId getId() {
        return id;
    }

    /**
     * Returns the day of the week the slot falls on.
     *
     * @return the weekday, in UTC (#22)
     */
    public Weekday getWeekday() {
        return id.weekday();
    }

    /**
     * Returns the time of day the slot starts.
     *
     * @return the start time, in UTC (#22)
     */
    public LocalTime getHourtime() {
        return id.hourtime();
    }

    /**
     * Returns whether the slot is still part of the agenda.
     *
     * @return the slot's status, never null
     */
    public TableScheduleStatus getStatus() {
        return status;
    }

    /**
     * Marks the slot live or removed.
     *
     * @param status the new status. Removing is what {@code TableScheduleService} does instead of
     *               deleting the row, so that putting the same slot back is an update rather than a
     *               collision with a key that still exists
     */
    public void setStatus(TableScheduleStatus status) {
        this.status = status;
    }

    /**
     * Returns when the slot was taken out of the agenda.
     *
     * @return the timestamp of the logical delete, or null while the slot is live
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete of the slot.
     *
     * @param deletedAt when it was removed, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
