package com.centraldungeon.users.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a person's administrative history: a role given, a role taken away, or an account
 * status that moved.
 *
 * <p>Two tables, one timeline. {@code user_role_changes} and {@code user_status_changes} are
 * separate rows because they carry different columns, but nobody reading the history cares which
 * table an entry came from - they read it as one sequence, so it arrives as one type with
 * {@code type} telling the screen which half of the fields to render.
 *
 * <p>It exists so the two audit tables are not write-only. Without it they would be born orphaned,
 * which is exactly the failure mode fase-3-admin-owner.md 7 warns about.
 *
 * @param id            the entry's identifier
 * @param type          {@code RoleGranted}, {@code RoleRevoked} or {@code StatusChanged}
 * @param role          which role moved, or null when the entry is a status change
 * @param fromStatus    where the account was, or null when the entry is a role change
 * @param toStatus      where it went, or null when the entry is a role change
 * @param changedByName who did it, already resolved to a display name - the screen shows a person,
 *                      not an id, exactly like {@code TableStatusChangeResponse}
 * @param justification why. Never null: both tables require it
 * @param createdAt     when it happened, in UTC
 */
public record UserAdminChangeResponse(
        String id,
        String type,
        @Nullable String role,
        @Nullable String fromStatus,
        @Nullable String toStatus,
        String changedByName,
        String justification,
        LocalDateTime createdAt) {
}
