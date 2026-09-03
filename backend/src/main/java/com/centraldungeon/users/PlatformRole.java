package com.centraldungeon.users;

/**
 * The four global roles (modelo-datos.md #37, #67). Acumulables, sin jerarquía: no
 * RoleHierarchy is registered anywhere (arquitectura.md 2.6). Values mirror {@code roles.name}.
 *
 * Not to be confused with {@code masters.master_type} (Primary/Secondary), a different
 * "Owner" concept scoped to a single table (decisiones.md #67).
 */
public enum PlatformRole {

    /** Can apply to tables and be accepted as a player. Everyone gets it on first login (#38). */
    PLAYER("Player"),

    /** Can create tables of their own - and nothing more. Running <em>this</em> table is a row in
     * {@code masters}, not this role (#135). */
    MASTER("Master"),

    /** Moderates the platform. Does not stack with OWNER: they are the same role at two scopes (#169). */
    ADMIN("Admin"),

    /** The platform's owner. Can do everything an admin can, by being listed on each endpoint rather
     * than by inheriting (#169). */
    OWNER("Owner");

    /** The name as it is stored in {@code roles.name} and written in a {@code @PreAuthorize}. */
    private final String roleName;

    /**
     * @param roleName the value in {@code roles.name} this constant mirrors
     */
    PlatformRole(String roleName) {
        this.roleName = roleName;
    }

    /**
     * Returns the name as the database spells it.
     *
     * @return the role name, matching a row of {@code roles}
     */
    public String roleName() {
        return roleName;
    }
}
