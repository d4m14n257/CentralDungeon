package com.centraldungeon.tables.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A master recording who was at one session (#36).
 *
 * <p>The whole roster travels at once because that is how it is filled in on screen - a master looks
 * at the list and says what happened - and because sending it as a set keeps a half-saved roster
 * from being a state the screen has to explain.
 *
 * <p>A player left out of the list is left alone: their row is not touched and not reset. Clearing
 * one is done by sending it as {@code Unknown}, explicitly.
 *
 * @param attendance one line per player being recorded. Empty is legal and does nothing
 */
public record RecordAttendanceRequest(@NotNull List<@Valid AttendanceEntryRequest> attendance) {
}
