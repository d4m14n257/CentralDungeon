package com.centraldungeon.matrix;

import com.centraldungeon.users.PlatformRole;
import java.util.Arrays;
import java.util.List;

/**
 * Who calls an endpoint in one cell of the role matrix, with exactly the platform roles the preflight
 * expects to read back from {@code /users/me}.
 *
 * <p>The relational actors hold only {@code Player} because belonging to a table has to be enough on
 * its own. {@link MatrixFixtures} gives each of them its relation the moment the cell starts, so a row
 * never has to set it up. Every actor has completed onboarding.
 */
enum MatrixActor {

    /** No token at all. */
    ANONYMOUS,

    /** A valid token of an account holding three roles whose status is flipped to blocked just before the call. */
    BLOCKED(PlatformRole.PLAYER, PlatformRole.MASTER, PlatformRole.ADMIN),

    /** Only {@code Player}, and no relation with the resource. */
    PLAYER(PlatformRole.PLAYER),

    /** Only {@code Master}, and no relation with the resource. */
    MASTER(PlatformRole.MASTER),

    /** Only {@code Admin}, and no relation with the resource. */
    ADMIN(PlatformRole.ADMIN),

    /** Only {@code Owner}, and no relation with the resource. */
    OWNER(PlatformRole.OWNER),

    /** The live {@code Primary} master of the row's table. */
    PRIMARY(PlatformRole.PLAYER),

    /** A live {@code Secondary} master of the row's table. */
    SECONDARY(PlatformRole.PLAYER),

    /** A co-master of the row's table whose row was removed. */
    REMOVED_SECONDARY(PlatformRole.PLAYER),

    /** Holds a {@code Candidate} application on the row's table. */
    CANDIDATE(PlatformRole.PLAYER),

    /** Holds a {@code Player} registration on the row's table. */
    TABLE_PLAYER(PlatformRole.PLAYER),

    /** The account a row hands the resource to, with {@code w.user(RESOURCE_OWNER)}. */
    RESOURCE_OWNER(PlatformRole.PLAYER),

    /** {@code Player} and {@code Master}. */
    PLAYER_MASTER(PlatformRole.PLAYER, PlatformRole.MASTER),

    /** {@code Master} and {@code Admin}. */
    MASTER_ADMIN(PlatformRole.MASTER, PlatformRole.ADMIN),

    /** {@code Master} and {@code Owner}. */
    MASTER_OWNER(PlatformRole.MASTER, PlatformRole.OWNER),

    /** {@code Player} and {@code Admin}. */
    PLAYER_ADMIN(PlatformRole.PLAYER, PlatformRole.ADMIN),

    /** {@code Player} and {@code Owner}. */
    PLAYER_OWNER(PlatformRole.PLAYER, PlatformRole.OWNER);

    /** The columns every row has to fill: an endpoint nobody tried on these has not been looked at. */
    static final List<MatrixActor> BASE = List.of(ANONYMOUS, BLOCKED, PLAYER, MASTER, ADMIN, OWNER);

    /** The roles this actor is granted. */
    private final List<PlatformRole> roles;

    MatrixActor(PlatformRole... roles) {
        this.roles = List.of(roles);
    }

    /** @return the platform roles to grant this actor */
    List<PlatformRole> roles() {
        return roles;
    }

    /** @return the role names {@code /users/me} has to answer with for this actor */
    List<String> roleNames() {
        return roles.stream().map(PlatformRole::roleName).toList();
    }

    /** @return whether this actor is the master, co-master or applicant of the row's table */
    boolean belongsToTable() {
        return Arrays.asList(PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER).contains(this);
    }
}
