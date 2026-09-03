package com.centraldungeon.tables.dto;

import com.centraldungeon.tables.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * One line of a roster on the way in: who, and what the master says about them.
 *
 * <p>Its own record rather than a reuse of {@link SessionAttendanceEntry}: the display name that one
 * carries is something the server writes, and accepting it back would mean taking a name from the
 * client for a field nothing reads.
 *
 * @param userId     the player. That they actually play at this table is checked in the service - a
 *                   roster is not something the caller gets to widen (#121)
 * @param attendance what to record. {@code Unknown} is legal and is how a master undoes a mistake;
 *                   it stays out of the denominator either way (#137)
 */
public record AttendanceEntryRequest(@NotBlank String userId, @NotNull AttendanceStatus attendance) {
}
