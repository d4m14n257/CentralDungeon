package com.centraldungeon.common.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Parses the one-line search language every search box of the app speaks (decisiones.md #164):
 *
 * <pre>
 *   juan                                     -> the endpoint's default criterion
 *   /discord_name juan                       -> that one field
 *   /user_name damian,carlos                 -> that field, either value
 *   /user_name juan /or /discord_name pablo  -> two criteria, joined
 * </pre>
 *
 * <b>The slash is the only separator.</b> Everything written after a {@code /field} is that
 * criterion's value, spaces included, until the next {@code /}: {@code /and} and {@code /or} join
 * criteria, a known {@code /field} starts the next one. A bare "and" or "or" is plain text - it has
 * to be, or no one could search for a value containing those words.
 *
 * <p>Rules, all of them deliberately forgiving - a search box must never blow up while being typed:
 * <ul>
 *   <li>A {@code /token} only starts a field when the caller declares that field as known. Anything
 *       else stays literal text, so a typo searches for itself instead of returning a 400.</li>
 *   <li>Commas split a criterion into alternatives; blanks between them are dropped.</li>
 *   <li>Two criteria with no explicit connector are joined with AND. A trailing connector is dropped.</li>
 *   <li>A field with no value emits nothing: a half-typed criterion searches nothing, not everything.</li>
 * </ul>
 *
 * The connectors are evaluated left to right, without precedence, because that is how the chips
 * read on screen - see {@link SearchConnector}.
 */
public final class SearchQueryParser {

    private static final String AND_TOKEN = "/and";
    private static final String OR_TOKEN = "/or";
    private static final String VALUE_SEPARATOR = ",";

    private SearchQueryParser() {
    }

    public static SearchQuery parse(@Nullable String raw, Set<String> knownFields) {
        if (raw == null || raw.isBlank()) {
            return SearchQuery.EMPTY;
        }
        return new Accumulator(knownFields).consume(raw.trim().split("\\s+")).result();
    }

    /** Mutable state of a single parse: the criterion being built plus everything already emitted. */
    private static final class Accumulator {

        private final Set<String> knownFields;
        private final List<SearchTerm> terms = new ArrayList<>();
        private final StringBuilder value = new StringBuilder();
        private @Nullable String field;
        private SearchConnector pendingConnector = SearchConnector.AND;

        private Accumulator(Set<String> knownFields) {
            this.knownFields = knownFields;
        }

        private Accumulator consume(String[] tokens) {
            for (String token : tokens) {
                SearchConnector connector = connectorToken(token);
                if (connector != null) {
                    flush();
                    pendingConnector = connector;
                    field = null;
                } else if (isKnownField(token)) {
                    flush();
                    field = token.substring(1).toLowerCase(Locale.ROOT);
                } else {
                    if (!value.isEmpty()) {
                        value.append(' ');
                    }
                    value.append(token);
                }
            }
            flush();
            return this;
        }

        private SearchQuery result() {
            return terms.isEmpty() ? SearchQuery.EMPTY : new SearchQuery(terms);
        }

        /** Emits the open criterion, if it has any value. A field with no value is not a criterion. */
        private void flush() {
            List<String> values = Arrays.stream(value.toString().split(VALUE_SEPARATOR))
                    .map(String::trim)
                    .filter(candidate -> !candidate.isEmpty())
                    .toList();
            value.setLength(0);
            if (values.isEmpty()) {
                field = null;
                return;
            }
            terms.add(new SearchTerm(field, values, terms.isEmpty() ? SearchConnector.AND : pendingConnector));
            field = null;
            pendingConnector = SearchConnector.AND;
        }

        private boolean isKnownField(String token) {
            return token.length() > 1
                    && token.charAt(0) == '/'
                    && knownFields.contains(token.substring(1).toLowerCase(Locale.ROOT));
        }

        private static @Nullable SearchConnector connectorToken(String token) {
            if (AND_TOKEN.equalsIgnoreCase(token)) {
                return SearchConnector.AND;
            }
            return OR_TOKEN.equalsIgnoreCase(token) ? SearchConnector.OR : null;
        }
    }
}
