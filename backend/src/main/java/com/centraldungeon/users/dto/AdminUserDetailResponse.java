package com.centraldungeon.users.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A person as the detail dialog of {@code /admin/users} shows them, and what all six mutating
 * endpoints answer with - so the screen never has to re-fetch after acting.
 *
 * <p>Not {@link UserDetailResponse}: that one is the caller's own view of themselves and carries
 * karma and {@code needsOnboarding}, which are answers to questions only the person themselves asks
 * (arquitectura.md 2.3). This one describes somebody else, and says only what administering them
 * needs.
 *
 * @param id              the person's identifier
 * @param discordUsername their Discord handle, as of their last login
 * @param name            their display name, or null while onboarding is incomplete (#134)
 * @param country         ISO 3166-1 alpha-2, or null while onboarding is incomplete
 * @param status          {@code Allowed}, {@code Blocked} or {@code Deleted}, as a string
 * @param roles           the roles they hold right now, in the declaration order of
 *                        {@code PlatformRole}: Player, Master, Admin, Owner
 * @param createdAt       when they first logged in, in UTC
 * @param updatedAt       when their row last changed, or null if it never did
 */
public record AdminUserDetailResponse(
        String id,
        String discordUsername,
        @Nullable String name,
        @Nullable String country,
        String status,
        List<String> roles,
        LocalDateTime createdAt,
        @Nullable LocalDateTime updatedAt) {
}
