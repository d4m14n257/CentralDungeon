package com.centraldungeon.users.dto;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The signed-in person's own view of themselves - what {@code GET /users/me} answers and what the
 * whole frontend shell is built from.
 *
 * <p>Richer than the summary because this one only ever describes the caller to themselves. Nothing
 * here is exposed about somebody else (arquitectura.md 2.3).
 *
 * @param id                the person's identifier
 * @param name              their display name, or null while onboarding is incomplete
 * @param country           where they play from, ISO 3166-1 alpha-2, or null while incomplete
 * @param karma             their community reputation (#97)
 * @param needsOnboarding   whether the app has to send them to the onboarding screen before
 *                          anything else (#134). Derived from name and country, so the frontend
 *                          never has to re-derive the rule
 * @param roles             the global roles they hold right now (#37, #67). Used to decide which
 *                          contexts the switcher offers - <b>never to authorize anything</b>, which
 *                          the backend does endpoint by endpoint (#103)
 * @param hasManagedTables  whether they run at least one table. Membership, not role: it is what
 *                          makes the Master context worth offering even to someone without the
 *                          Master role, and the reason #135 keeps the two apart
 */
public record UserDetailResponse(
        String id,
        @Nullable String name,
        @Nullable String country,
        int karma,
        boolean needsOnboarding,
        Set<String> roles,
        boolean hasManagedTables) {
}
