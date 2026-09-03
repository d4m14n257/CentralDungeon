package com.centraldungeon.tables.dto;

import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A table as its own screen shows it: everything the summary has, plus the long text, the agenda,
 * the catalogs and the people who run it.
 *
 * <p>Separate from {@link GameTableSummaryResponse} because returning this for fifty rows of the
 * explorer would load fifty times the relations nobody is going to look at (arquitectura.md 2.3).
 *
 * @param id            the table's identifier
 * @param name          the table's title
 * @param description   what the table is about, as sanitized rich text (#62)
 * @param permitted     the house rules, as sanitized rich text
 * @param requirements  what is asked of a player to be accepted, as sanitized rich text
 * @param tableTypeName how the table is run, resolved to its label. Null when none was chosen
 * @param status        where the table is in its lifecycle, as a string (arquitectura.md 2.3)
 * @param maxPlayers    the player cap (#24), or null for no cap
 * @param playerCount   how many people are accepted right now. Derived, never stored
 * @param startDate     when the first session happens, in UTC. The frontend converts (#22, #111)
 * @param duration      how long one session lasts
 * @param totalSessions how many sessions are planned (#26)
 * @param schedule      the weekly agenda, in UTC (#22), ordered as a week reads
 * @param systems       the game systems the table uses, each under the alias its master picked and
 *                      never rewritten to the group's canonical entry (#58)
 * @param tags          the tags the table is labelled with, same rule
 * @param platforms     where the table is played, same rule
 * @param masters       everyone who runs the table, Primary first. Never null; an Unassigned table
 *                      has an empty list
 * @param createdAt     when the table was created, in UTC
 * @param closedAt      when it entered Finished or Canceled (#180), or null while it is still going.
 *                      It is what starts the two-week profile visibility window of #44
 * @param scheduleConflict whether this table's agenda overlaps something the <b>actor of the
 *                      token</b> is already committed to (#178). It is what lets the apply button
 *                      say why it is disabled instead of only being grey. Computed per request and
 *                      never for an id from the URL (#121); false on the reads where no actor is
 *                      involved, such as a master looking at their own table
 */
public record GameTableDetailResponse(
        String id,
        String name,
        @Nullable String description,
        @Nullable String permitted,
        @Nullable String requirements,
        @Nullable String tableTypeName,
        String status,
        @Nullable Integer maxPlayers,
        int playerCount,
        @Nullable LocalDateTime startDate,
        @Nullable LocalTime duration,
        @Nullable Integer totalSessions,
        List<TableScheduleEntry> schedule,
        List<CatalogValueResponse> systems,
        List<CatalogValueResponse> tags,
        List<CatalogValueResponse> platforms,
        List<MasterSummaryResponse> masters,
        LocalDateTime createdAt,
        @Nullable LocalDateTime closedAt,
        boolean scheduleConflict) {
}
