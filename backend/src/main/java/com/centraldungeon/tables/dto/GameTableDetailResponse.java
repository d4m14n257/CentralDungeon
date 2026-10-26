package com.centraldungeon.tables.dto;

import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.files.dto.SharedFileResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A table as its own screen shows it: everything the summary has, plus the long text, the agenda,
 * the catalogs and the people who run it.
 *
 * <p>Separate from {@link GameTableSummaryResponse} because returning this for fifty rows of the
 * explorer would load fifty times the relations nobody is going to look at (arquitectura §2.3).
 *
 * @param id            the table's identifier
 * @param name          the table's title
 * @param description   what the table is about, as sanitized rich text (ADR-03 §8)
 * @param permitted     the house rules, as sanitized rich text
 * @param requirements  what is asked of a player to be accepted, as sanitized rich text
 * @param tableTypeName how the table is run, resolved to its label. Null when none was chosen
 * @param tableTypeCode the identifier the frontend translates by, when the application shipped
 *                      that type. Null for one a person named, whose label is read verbatim (ADR-23 §4)
 * @param status        where the table is in its lifecycle, as a string (arquitectura §2.3)
 * @param maxPlayers    the player cap (ADR-07 §5), or null for no cap
 * @param playerCount   how many people are accepted right now. Derived, never stored
 * @param startDate     the day the table starts running (ADR-07 §4). A plain date, shown as it is:
 *                      it carries no hour and no zone to convert
 * @param totalSessions how many sessions are planned (ADR-08 §3)
 * @param schedule      the weekly agenda, in UTC (ADR-03 §6), ordered as a week reads
 * @param sessions      the materialized calendar, first to last (ADR-08 §3). Dates rather than the
 *                      weekly shape: once a table has been paused and resumed the two diverge, and
 *                      the dates are what somebody deciding whether to apply actually needs. Empty
 *                      until the table opens, and while it is paused it carries only what already
 *                      happened (ADR-07 §2)
 * @param systems       the game systems the table uses, each under the alias its master picked and
 *                      never rewritten to the group's canonical entry (ADR-12 §4)
 * @param tags          the tags the table is labelled with, same rule
 * @param platforms     where the table is played, same rule
 * @param masters       everyone who runs the table, Primary first. Never null; an Unassigned table
 *                      has an empty list
 * @param files         the files the table shares with its candidates and players. Only the shared
 *                      ones: a private attachment is absent, not listed and locked, because a reader
 *                      has no reason to know their master keeps notes (ADR-14 §1). Travels here for the
 *                      same reason {@code sessions} does - this read already settles who may see the
 *                      table (ADR-09 §4), so the files inherit that one answer
 * @param createdAt     when the table was created, in UTC
 * @param closedAt      when it entered Finished or Canceled (ADR-07 §7), or null while it is still going.
 *                      It is what starts the two-week profile visibility window of ADR-10 §2
 * @param scheduleConflict whether this table's agenda overlaps something the <b>actor of the
 *                      token</b> is already committed to (ADR-08 §2). It is what lets the apply button
 *                      say why it is disabled instead of only being grey. Computed per request and
 *                      never for an id from the URL (ADR-05 §5); false on the reads where no actor is
 *                      involved, such as a master looking at their own table
 * @param applied       whether the <b>actor of the token</b> holds a live application on it - Candidate or
 *                      Player (ADR-09 §1) - so the apply button can say they already did. False where no
 *                      actor is involved
 * @param joined        whether the actor of the token is already a Player of the table - the apply button
 *                      says they are in instead of "you applied". False where no actor is involved
 */
public record GameTableDetailResponse(
        String id,
        String name,
        @Nullable String description,
        @Nullable String permitted,
        @Nullable String requirements,
        @Nullable String tableTypeName,
        @Nullable String tableTypeCode,
        String status,
        @Nullable Integer maxPlayers,
        int playerCount,
        @Nullable LocalDate startDate,
        @Nullable Integer totalSessions,
        List<TableScheduleEntry> schedule,
        List<PublicSessionResponse> sessions,
        List<CatalogValueResponse> systems,
        List<CatalogValueResponse> tags,
        List<CatalogValueResponse> platforms,
        List<MasterSummaryResponse> masters,
        List<SharedFileResponse> files,
        LocalDateTime createdAt,
        @Nullable LocalDateTime closedAt,
        boolean scheduleConflict,
        boolean applied,
        boolean joined) {
}
