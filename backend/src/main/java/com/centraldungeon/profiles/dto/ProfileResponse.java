package com.centraldungeon.profiles.dto;

import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Somebody's public profile, as read under the five visibility rules of modelo-datos.md §5
 * ("Visibilidad de perfiles": #41, #44, #45, #47).
 *
 * <p><b>No {@code karma}, no {@code comments} - not even {@code null}</b> (decisiones.md #248): both
 * are F5. A field that promises a number the frontend has no way to fill teaches the first component
 * that reads it to ask for one, and shows a gap where a number belongs instead.
 *
 * @param id         the person's identifier
 * @param name       their display name, or null while they have not completed onboarding (#134)
 * @param country    where they play from, ISO 3166-1 alpha-2, or null while incomplete
 * @param roles      the global roles they hold right now (#37, #67) - shown here, never used to
 *                   authorize anything: the backend decides that endpoint by endpoint (#103)
 * @param attendance their historical attendance across every table they have played (#137): three
 *                   numbers, and {@code Unknown} never among them
 */
public record ProfileResponse(
        String id, @Nullable String name, @Nullable String country, Set<String> roles, AttendanceSummaryResponse attendance) {
}
