package com.centraldungeon.users;

/**
 * One live role of one person. Internal projection of {@link UserRoleRepository}, not a DTO: it
 * never crosses HTTP - {@code AdminUserSummaryResponse} carries the names.
 *
 * <p>It exists so a page of twenty people costs one query instead of twenty calls to
 * {@link UserRoleRepository#findActiveRoleNames}. Somebody holding no role never appears in the
 * result, so a missing entry means "no roles", not "not loaded".
 *
 * @param userId   the person
 * @param roleName the role they hold, as {@code roles.name} spells it
 */
public record UserRoleGrant(String userId, String roleName) {
}
