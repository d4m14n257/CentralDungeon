package com.centraldungeon.tables;

import com.centraldungeon.catalogs.CatalogType;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The fields the explorer's search box accepts behind a {@code /prefix} (#164).
 *
 * <p><b>Every wire name is prefixed with the entity</b>, like {@code UserSearchField} and
 * {@code FileSearchField} before it (#239). Two screens both offering a bare {@code /name} would mean
 * the same command searched different things depending on where it was typed.
 *
 * <p>The default criterion is the table's name, because it is what somebody half-remembers about a
 * table they saw last week. The other three are the reason the search language was designed at all:
 * #164 was written thinking of {@code /tag}, and until F2.1 the command existed in no box.
 *
 * <p><b>Three of the four are not columns</b>, and that is the whole difficulty of this enum. A
 * table's systems, tags and platforms live in bridge tables, and the value they point at is the alias
 * its master chose rather than the group's canonical entry (#56). So the text is resolved to a set of
 * catalog ids first - {@code AbstractCatalogService.resolveGroupIdsByName} - and only then does the
 * specification ask whether the table links to any of them. Which catalog to resolve against is
 * {@link #catalog()}; a field with none is plain text on the table itself.
 *
 * <p><b>They are free text and not a closed list</b> (#246), unlike {@code /file_type}: catalog
 * values are hundreds, they grow whenever a master proposes one (#55), and they are exactly the words
 * people use. The synonym group is what forgives typing them differently, so a list of options would
 * be offering a service the grouping already provides.
 */
public enum GameTableSearchField {

    /** The table's name. Also what a criterion with no {@code /field} prefix searches. */
    NAME("table_name", "name", null),

    /** The game system, resolved through its synonym group (#54, #56). */
    SYSTEM("table_system", null, CatalogType.SYSTEMS),

    /** The tag, resolved through its synonym group. The command #164 was designed for. */
    TAG("table_tag", null, CatalogType.TAGS),

    /** Where the table is played, resolved through its synonym group. */
    PLATFORM("table_platform", null, CatalogType.PLATFORMS);

    /** What the person types after the slash, and what the chip shows. */
    private final String wireName;

    /** The JPA attribute path on {@code GameTable}, or null when the field is not a column. */
    private final @Nullable String attribute;

    /** The catalog this field resolves against, or null when it searches the table itself. */
    private final @Nullable CatalogType catalog;

    /**
     * @param wireName  what the person types after the slash
     * @param attribute the JPA attribute it maps to, or null for a catalog field
     * @param catalog   the catalog to resolve against, or null for a plain text field
     */
    GameTableSearchField(String wireName, @Nullable String attribute, @Nullable CatalogType catalog) {
        this.wireName = wireName;
        this.attribute = attribute;
        this.catalog = catalog;
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
     * @return the attribute path, or null when the field lives in a bridge table instead
     */
    @Nullable String attribute() {
        return attribute;
    }

    /**
     * Returns the catalog whose synonym groups resolve this field.
     *
     * <p>Null is not «no catalog configured»: it means the field is a column on the table itself and
     * needs no resolution at all.
     *
     * @return the catalog, or null when the field is plain text on {@code game_tables}
     */
    public @Nullable CatalogType catalog() {
        return catalog;
    }

    /**
     * The set the parser needs in order to tell a {@code /field} from literal text. A slash token
     * that is not in here stays text, so a typo searches for itself instead of returning a 400.
     *
     * @return every wire name this search understands
     */
    public static Set<String> wireNames() {
        return Arrays.stream(values()).map(GameTableSearchField::wireName).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Resolves what somebody typed after a slash.
     *
     * @param wireName the text between the slash and the space, in any case
     * @return the field it names, or empty when it names none - which the parser turns into literal
     *         text rather than an error (#164)
     */
    public static Optional<GameTableSearchField> fromWireName(String wireName) {
        String normalized = wireName.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(field -> field.wireName.equals(normalized)).findFirst();
    }
}
