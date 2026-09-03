package com.centraldungeon.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link SessionAttendance}: one person is recorded once per session. Same pattern
 * as {@link MasterId} and {@link TableScheduleId}.
 *
 * @param tableSessionId the session being recorded
 * @param userId         the player whose attendance it is
 */
@Embeddable
public record SessionAttendanceId(
        @Column(name = "table_session_id", length = 64) String tableSessionId,
        @Column(name = "user_id", length = 64) String userId)
        implements Serializable {
}
