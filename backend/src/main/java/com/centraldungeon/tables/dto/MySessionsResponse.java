package com.centraldungeon.tables.dto;

import java.util.List;

/**
 * What {@code /my/tables/:id} reads: a player's own calendar and their own attendance on that table.
 *
 * <p>The two travel together because they are one answer to one question - "how is my table going?"
 * - and splitting them would cost the screen a second round trip to say something it always shows
 * next to the first.
 *
 * @param sessions the calendar. While the table is paused the pending sessions are not in it: a
 *                 pause freezes the agenda and there is no date to promise (#32, #33)
 * @param summary  their historical attendance on this table, as three numbers (#137)
 */
public record MySessionsResponse(List<PlayerSessionResponse> sessions, AttendanceSummaryResponse summary) {
}
