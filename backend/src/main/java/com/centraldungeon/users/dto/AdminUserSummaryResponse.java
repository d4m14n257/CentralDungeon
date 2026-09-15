package com.centraldungeon.users.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A person as one row of {@code /admin/users}.
 *
 * <p>Richer than {@link UserSummaryResponse}, which is the picker's view and deliberately says
 * nothing about status or roles - administering people is the one screen where both are the point
 * (arquitectura.md 2.3).
 *
 * <p><b>No {@code discordId}</b>: it is not needed to administer anybody and it is a third party's
 * identifier. No karma either - that is the profile's business, not this screen's.
 *
 * @param id              the person's identifier
 * @param discordUsername their Discord handle, as of their last login
 * @param name            their display name, or null while onboarding is incomplete (#134)
 * @param country         ISO 3166-1 alpha-2, or null while onboarding is incomplete
 * @param status          whether the account may be used: {@code Allowed}, {@code Blocked} or
 *                        {@code Deleted}, as a string like every other enum on the wire
 * @param roles           the roles they hold right now, ordered Player, Master, Admin, Owner - the
 *                        declaration order of {@code PlatformRole}, so the chips never reshuffle
 *                        between two rows of the same table
 * @param createdAt       when they first logged in, in UTC
 */
public record AdminUserSummaryResponse(
        String id,
        String discordUsername,
        @Nullable String name,
        @Nullable String country,
        String status,
        List<String> roles,
        LocalDateTime createdAt) {
}
