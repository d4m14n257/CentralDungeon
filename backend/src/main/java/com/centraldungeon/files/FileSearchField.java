package com.centraldungeon.files;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fields the /admin/files search box accepts behind a {@code /prefix} (#164).
 *
 * <p>The default criterion is the filename, because it is the only thing anybody remembers about a
 * file. The other two exist because an admin's actual questions are "who uploaded this" and "what
 * kind of file am I looking at" - and without them those questions turn into scrolling.
 */
public enum FileSearchField {

    /** The original filename. Also what a criterion with no {@code /field} prefix searches. */
    NAME("name", "name"),

    /**
     * Who uploaded it. Matched on the Discord username rather than the chosen name, because that one
     * is always there - {@code users.name} is nullable and empty for anybody who never set one, so
     * searching it would silently miss those people.
     */
    OWNER("owner", "userCreated.discordUsername"),

    /** The declared MIME type - how "show me the PDFs" is asked. */
    TYPE("type", "mimeType");

    /** What the person types after the slash, and what the chip shows. */
    private final String wireName;

    /** The JPA attribute path the wire name maps to. */
    private final String attribute;

    /**
     * @param wireName  what the person types after the slash
     * @param attribute the JPA attribute it maps to
     */
    FileSearchField(String wireName, String attribute) {
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
     * Returns the JPA attribute this field maps to.
     *
     * @return the attribute path, for the Criteria API
     */
    String attribute() {
        return attribute;
    }

    /**
     * The set the parser needs in order to tell a {@code /field} from literal text. A slash token
     * that is not in here stays text, so a typo searches for itself instead of returning a 400.
     *
     * @return every wire name this search understands
     */
    public static Set<String> wireNames() {
        return Arrays.stream(values()).map(FileSearchField::wireName).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Resolves a wire name to its field, case-insensitively.
     *
     * @param wireName the token that followed the slash
     * @return the matching field, or empty when it names no field
     */
    static Optional<FileSearchField> fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(field -> field.wireName.equals(normalized)).findFirst();
    }
}
