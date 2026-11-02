package com.centraldungeon.profiles.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One table a person played to the end, as the list behind a profile's «finished tables» count shows
 * it: only what the table's public detail already says, so it exposes nothing the profile did not.
 *
 * @param id       the table, to open its public detail
 * @param name     its title
 * @param systems  the names of its game systems, alphabetical. Empty when it declared none
 * @param masters  the display names of the people who run it, the primary master first
 * @param closedAt when it finished (UTC). Null only for a row that predates that column being stamped
 */
public record FinishedTableResponse(
        String id, String name, List<String> systems, List<String> masters, @Nullable LocalDateTime closedAt) {
}
