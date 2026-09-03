package com.centraldungeon.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.time.LocalTime;

/**
 * Composite key of {@link TableSchedule}: a table plays at a given weekday and time once. Same
 * pattern as {@link MasterId}.
 *
 * @param gameTableId the table the slot belongs to
 * @param weekday     the day of the week, <b>in UTC</b> (#22) - not the day the players call it
 * @param hourtime    the time of day the slot starts, in UTC, truncated to the minute by
 *                    {@code TableScheduleService} before it ever gets here
 */
@Embeddable
public record TableScheduleId(
        @Column(name = "game_table_id", length = 64) String gameTableId,
        @Enumerated(EnumType.STRING) @Column(name = "weekday", length = 16) Weekday weekday,
        @Column(name = "hourtime") LocalTime hourtime)
        implements Serializable {
}
