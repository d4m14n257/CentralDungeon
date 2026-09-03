package com.centraldungeon.users;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fields a user search box accepts behind a {@code /prefix} (decisiones.md #164). The wire name
 * is what the person types and what the chip shows; the attribute is the JPA path it maps to.
 *
 * <p>Typing nothing in particular searches by DISCORD_NAME <em>or</em> USER_NAME: someone looking
 * for a player knows one of the two names, not which one the system stores where.
 */
public enum UserSearchField {

    DISCORD_NAME("discord_name", "discordUsername"),
    USER_NAME("user_name", "name");

    private final String wireName;
    private final String attribute;

    UserSearchField(String wireName, String attribute) {
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

    String attribute() {
        return attribute;
    }

    /**
     * The set the parser needs in order to tell a {@code /field} from literal text.
     *
     * @return every wire name a user search accepts
     */
    public static Set<String> wireNames() {
        return Arrays.stream(values()).map(UserSearchField::wireName).collect(Collectors.toUnmodifiableSet());
    }

    static Optional<UserSearchField> fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(field -> field.wireName.equals(normalized)).findFirst();
    }
}
