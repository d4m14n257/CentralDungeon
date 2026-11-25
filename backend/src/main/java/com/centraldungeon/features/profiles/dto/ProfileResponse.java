package com.centraldungeon.features.profiles.dto;

import com.centraldungeon.features.tables.dto.AttendanceSummaryResponse;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Somebody's public profile, as read under the five profile visibility rules.
 *
 * <p><b>No {@code karma}, no {@code comments} - not even {@code null}</b>: both
 * are still to be built. A field that promises a number the frontend has no way to fill teaches the first component
 * that reads it to ask for one, and shows a gap where a number belongs instead.
 *
 * @param id              the person's identifier
 * @param discordUsername their Discord handle, as of their last login - how the community finds them
 *                        on the server, and what a master reads first on a candidate's card
 * @param name            their display name, or null while they have not completed onboarding
 * @param country    where they play from, ISO 3166-1 alpha-2, or null while incomplete
 * @param roles      the global roles they hold right now - shown here, never used to
 *                   authorize anything: the backend decides that endpoint by endpoint
 * @param attendance their historical attendance across every table they have played: three
 *                   numbers, and {@code Unknown} never among them
 * @param finishedTables how many tables they played to the end: accepted as a player at a table that
 *                       is now finished. Their track record in one number, without karma
 */
public record ProfileResponse(
        String id,
        String discordUsername,
        @Nullable String name,
        @Nullable String country,
        Set<String> roles,
        AttendanceSummaryResponse attendance,
        long finishedTables) {
}
