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
 * <p><b>Six commands, and only two of them are plain columns.</b> {@code /table_name} and
 * {@code /table_status} are; the three catalog ones are resolved through synonym groups, and
 * {@code /table_master} is a subquery over {@code masters}. {@link #attribute()} being null is how
 * the specification tells them apart, together with {@link #catalog()}.
 *
 * <p><b>Three of the six are not columns</b>, and that is the whole difficulty of this enum. A
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
    PLATFORM("table_platform", null, CatalogType.PLATFORMS),

    /**
     * Where the table is in its lifecycle, matched whole: {@code Draft}, {@code Preparation},
     * {@code Opened}… The one command of this search with a <b>closed list</b>, the same shape
     * {@code ApprovalSearchField.STATUS} has - so the frontend declares it with {@code values:} and
     * nobody has to remember the spelling.
     *
     * <p>Free text everywhere else in this enum is a decision about catalogs (#246), not a style: a
     * catalog has hundreds of values that grow whenever a master proposes one, and the nine states of
     * a table are neither. An unknown state still matches nothing rather than answering 400
     * (arquitectura.md §2.5).
     *
     * <p>Useful on {@code /admin/tables}, where the listing spans every state (#176). The explorer
     * accepts it too, because the search language belongs to the entity and not to the screen (#239),
     * and <b>that opens no hole</b> - for a structural reason and not a hopeful one. The visible
     * statuses are a top-level {@code AND} in {@code forExplorer} and are never folded into what the
     * reader typed, so the worst a player can write is
     * {@code /table_name x /or /table_status Draft}, which becomes
     * {@code status IN (Opened, InProgress) AND (name LIKE '%x%' OR status = 'Draft')}. The second
     * half of that can only ever be unsatisfiable where it matters: no row is both visible and a
     * draft. Zero results, never somebody else's draft.
     */
    STATUS("table_status", "status", null),

    /**
     * Who runs the table, <b>by name and never by id</b>, like every other person criterion in the
     * application ({@code /requested_by}, {@code /owner}). Substring, case-insensitive, over both the
     * display name and the Discord handle, because whoever is searching knows one of the two and not
     * which one the system keeps where.
     *
     * <p>Live master rows only: somebody removed as a co-master (#216) does not run the table any
     * more, and finding it by their name would be answering a question about the past.
     */
    MASTER("table_master", null, null);

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
