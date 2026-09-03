package com.centraldungeon.catalogs;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fields a catalog search box accepts behind a {@code /prefix} (decisiones.md #164).
 *
 * <p>Only one for now, and it is also the default criterion: typing nothing in particular searches
 * by name, which is the only thing anyone knows about a catalog value. The enum exists anyway
 * because it is what makes {@code /name algo} work instead of being read as literal text, and
 * because a second field (the canonical entry's name) is the obvious next one.
 */
public enum CatalogSearchField {

    /** The value's own name. Also what a criterion with no {@code /field} prefix searches. */
    NAME("name", "name");

    /** What the person types after the slash, and what the chip shows. */
    private final String wireName;

    /** The JPA attribute path the wire name maps to. */
    private final String attribute;

    /**
     * @param wireName what the person types after the slash
     * @param attribute the JPA attribute it maps to
     */
    CatalogSearchField(String wireName, String attribute) {
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
     * @return every wire name this catalog understands
     */
    public static Set<String> wireNames() {
        return Arrays.stream(values()).map(CatalogSearchField::wireName).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Resolves a wire name to its field, case-insensitively.
     *
     * @param wireName the token that followed the slash
     * @return the matching field, or empty when it names no field
     */
    static Optional<CatalogSearchField> fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(field -> field.wireName.equals(normalized)).findFirst();
    }
}
