package com.centraldungeon.users;

/**
 * The four global roles (modelo-datos.md #37, #67). Acumulables, sin jerarquía: no
 * RoleHierarchy is registered anywhere (arquitectura.md 2.6). Values mirror {@code roles.name}.
 *
 * Not to be confused with {@code masters.master_type} (Primary/Secondary), a different
 * "Owner" concept scoped to a single table (decisiones.md #67).
 */
public enum PlatformRole {
    PLAYER("Player"),
    MASTER("Master"),
    ADMIN("Admin"),
    OWNER("Owner");

    private final String roleName;

    PlatformRole(String roleName) {
        this.roleName = roleName;
    }

    public String roleName() {
        return roleName;
    }
}
