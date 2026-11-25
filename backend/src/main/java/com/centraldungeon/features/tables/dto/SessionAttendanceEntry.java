package com.centraldungeon.features.tables.dto;

import com.centraldungeon.features.tables.AttendanceStatus;

/**
 * One line of a session's roster, on the way out: a player and what was recorded for them.
 *
 * <p>The roster is built on the server from the table's active players and never from what the
 * client sends back, so a session always lists everybody who plays there - including the ones with
 * no row yet, who come back as {@code Unknown}.
 *
 * @param userId     the player
 * @param userName   their display name, so the roster reads without a second call
 * @param attendance what was recorded, or {@code Unknown} when nothing was
 */
public record SessionAttendanceEntry(String userId, String userName, AttendanceStatus attendance) {
}
