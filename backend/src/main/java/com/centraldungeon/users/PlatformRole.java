package com.centraldungeon.users;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The four global roles (modelo-datos.md #37, #67). They stack, and there is no hierarchy among
 * them: no RoleHierarchy is registered anywhere (arquitectura.md 2.6). Values mirror
 * {@code roles.name}.
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
     * <p><b>{@code @JsonValue} is load-bearing, in both directions.</b> This is the only enum in the
     * application whose constants are not spelled the way the wire spells them - the constants are
     * {@code PLAYER}/{@code MASTER}/{@code ADMIN}/{@code OWNER} and the wire says {@code "Player"},
     * because the wire mirrors {@code roles.name}. Everywhere else ({@code MasterType.Primary},
     * {@code UserStatus.Allowed}, {@code FileType.Public}) the two already coincide, which is why no
     * other enum needed this.
     *
     * <p>Without it, Jackson writes and reads {@code Enum.name()}: responses would say
     * {@code "PLAYER"} while every response built through {@code UserMapper} says {@code "Player"},
     * and a request carrying the value the API itself published would be rejected with a 400. With
     * it, one method defines the spelling for reading and for writing at once, so the two can never
     * drift.
     *
     * @return the role name, matching a row of {@code roles}
     */
    @JsonValue
    public String roleName() {
        return roleName;
    }

    /**
     * Looks a constant up by the name the database spells.
     *
     * <p><b>The second door.</b> A role name reaches the backend two ways: as JSON in the body of a
     * grant or a revoke, resolved by {@link #roleName()}'s {@code @JsonValue}, and as literal text
     * inside {@code ?q=} when somebody types {@code /role Admin}, resolved here. Both accept exactly
     * what the API publishes - {@code "Player"}, {@code "Master"}, {@code "Admin"}, {@code "Owner"} -
     * which is what lets the frontend send back the value it read.
     *
     * <p>They differ on purpose in how forgiving they are, and the asymmetry is the interesting
     * part. A body is exact: a request naming {@code "ADMIN"} is malformed and gets a 400, loudly.
     * A search box is not: it has to survive being typed into, so this one matches case-insensitively
     * and an unknown name is simply nobody rather than an error. That forgiveness has a cost worth
     * naming - a {@code /role} value this method cannot resolve produces a filter matching zero
     * rows, and zero rows looks like an empty platform rather than like a typo.
     *
     * @param roleName a value of {@code roles.name}, in any case
     * @return the matching constant, or empty when the name is not one of the four. Empty is a
     *         normal answer and not an error: {@code /admin/users?q=/role Wizard} has to match
     *         nothing, not answer 400 (arquitectura.md 2.5)
     */
    public static Optional<PlatformRole> fromRoleName(String roleName) {
        String normalized = roleName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(role -> role.roleName.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    /**
     * Puts a person's role names in the order this enum declares them - Player, Master, Admin,
     * Owner - and drops anything that is not one of the four.
     *
     * <p>Order matters because roles reach the screen as a row of chips: the same person listed
     * twice has to read the same both times, and a {@code Set<String>} coming out of a query has no
     * order to promise. Declaring it here rather than in the frontend keeps the one answer in the
     * one place that already knows what the four roles are.
     *
     * @param roleNames the names the person holds, in whatever order the query returned them
     * @return those names, ordered and deduplicated. Never null; an empty input gives an empty list
     */
    public static List<String> ordered(Collection<String> roleNames) {
        return Arrays.stream(values())
                .map(PlatformRole::roleName)
                .filter(roleNames::contains)
                .toList();
    }
}
