package com.centraldungeon.tables.dto;

import com.centraldungeon.tables.AttendanceStatus;

/**
 * One line of a session's roster, on the way out: a player and what was recorded for them (#36).
 *
 * <p>The roster is built on the server from the table's active players and never from what the
 * client sends back, so a session always lists everybody who plays there - including the ones with
 * no row yet, who come back as {@code Unknown}.
 *
 * @param userId     the player
 * @param userName   their display name, so the roster reads without a second call
 * @param attendance what was recorded, or {@code Unknown} when nothing was (#137)
 */
public record SessionAttendanceEntry(String userId, String userName, AttendanceStatus attendance) {
}
