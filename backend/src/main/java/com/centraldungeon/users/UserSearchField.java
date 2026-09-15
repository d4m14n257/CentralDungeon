package com.centraldungeon.users;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The fields a user search box accepts behind a {@code /prefix} (decisiones.md #164). The wire name
 * is what the person types and what the chip shows; the attribute is the JPA path it maps to.
 *
 * <p>Typing nothing in particular searches by DISCORD_NAME <em>or</em> USER_NAME: someone looking
 * for a player knows one of the two names, not which one the system stores where.
 *
 * <p><b>Two audiences, two vocabularies.</b> {@link #wireNames()} is what {@code GET /users/search}
 * accepts - the picker, where the only question is who somebody is. {@link #adminWireNames()} adds
 * {@code /role} and {@code /status} for {@code /admin/users}, where the question is what they hold
 * and whether their account works. The separation is not cosmetic: the parser searches an
 * unrecognized {@code /field} as literal text (never a 400), so a picker user typing
 * {@code /status Blocked} searches for that text and learns nothing about anyone's status.
 */
public enum UserSearchField {

    /** Their Discord handle. Substring, case-insensitive. */
    DISCORD_NAME("discord_name", "discordUsername"),

    /** The display name they chose at onboarding. Substring, case-insensitive. */
    USER_NAME("user_name", "name"),

    /**
     * A role they hold <em>right now</em>, matched whole: {@code Player}, {@code Master},
     * {@code Admin} or {@code Owner}. Admin vocabulary only. No JPA attribute of {@code User} backs
     * it - it resolves through {@code users_roles}, which is why the attribute is null.
     */
    ROLE("role", null),

    /**
     * The account's status, matched whole: {@code Allowed}, {@code Blocked} or {@code Deleted}.
     * Admin vocabulary only. It maps to a real column, but to an equality and not to a LIKE, so it
     * does not travel through {@link #attribute()} either.
     */
    STATUS("status", null);

    private final String wireName;

    private final @Nullable String attribute;

    UserSearchField(String wireName, @Nullable String attribute) {
        this.wireName = wireName;
        this.attribute = attribute;
    }

    /**
     * Returns what the person types after the slash.
     *
     * @return the wire name, lowercase
     */
    public String wireName() {
        return wireName;
    }

    /**
     * The JPA path this field searches with a LIKE.
     *
     * @return the attribute name, or null for a field that is not a substring match over a column of
     *         {@code users} - {@link #ROLE} and {@link #STATUS}
     */
    @Nullable String attribute() {
        return attribute;
    }

    /**
     * The set the parser needs in order to tell a {@code /field} from literal text, for the picker.
     *
     * @return the wire names {@code GET /users/search} accepts: the two names and nothing else
     */
    public static Set<String> wireNames() {
        return Set.of(DISCORD_NAME.wireName, USER_NAME.wireName);
    }

    /**
     * The same set for {@code /admin/users}, which also filters by role and by status.
     *
     * @return every wire name the admin search accepts
     */
    public static Set<String> adminWireNames() {
        return Arrays.stream(values()).map(UserSearchField::wireName).collect(Collectors.toUnmodifiableSet());
    }

    static Optional<UserSearchField> fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(field -> field.wireName.equals(normalized)).findFirst();
    }
}
