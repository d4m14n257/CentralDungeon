package com.centraldungeon.common.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Parses the one-line search language every search box of the app speaks (decisiones.md #164):
 *
 * <pre>
 *   juan                                    -> the endpoint's default criterion
 *   /discord_name juan                      -> that one field
 *   /user_name juan or /discord_name pablo  -> two criteria, joined
 * </pre>
 *
 * Rules, all of them deliberately forgiving - a search box must never blow up while being typed:
 * <ul>
 *   <li>A {@code /token} only starts a field when the caller declares that field as known. Anything
 *       else stays literal text, so a typo searches for itself instead of returning a 400.</li>
 *   <li>{@code and} / {@code or} are reserved words (case-insensitive) whenever a term is already
 *       open; they set how the <em>next</em> term joins. A trailing one is dropped. Two terms with
 *       no explicit connector are joined with AND.</li>
 *   <li>A field with no value emits nothing: a half-typed chip searches nothing, not everything.</li>
 * </ul>
 *
 * The connectors are evaluated left to right, without precedence, because that is how the chips
 * read on screen - see {@link SearchConnector}.
 */
public final class SearchQueryParser {

    private static final String AND_KEYWORD = "and";
    private static final String OR_KEYWORD = "or";

    private SearchQueryParser() {
    }

    public static SearchQuery parse(@Nullable String raw, Set<String> knownFields) {
        if (raw == null || raw.isBlank()) {
            return SearchQuery.EMPTY;
        }
        return new Accumulator(knownFields).consume(raw.trim().split("\\s+")).result();
    }

    /** Mutable state of a single parse: the term being built plus everything already emitted. */
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
                SearchConnector connector = keywordConnector(token);
                if (connector != null && (!value.isEmpty() || !terms.isEmpty())) {
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

        /** Emits the open term, if it has a value. A field with no value is not a term. */
        private void flush() {
            if (value.isEmpty()) {
                return;
            }
            terms.add(new SearchTerm(field, value.toString(), terms.isEmpty() ? SearchConnector.AND : pendingConnector));
            value.setLength(0);
            field = null;
            pendingConnector = SearchConnector.AND;
        }

        private boolean isKnownField(String token) {
            return token.length() > 1
                    && token.charAt(0) == '/'
                    && knownFields.contains(token.substring(1).toLowerCase(Locale.ROOT));
        }

        private static @Nullable SearchConnector keywordConnector(String token) {
            if (AND_KEYWORD.equalsIgnoreCase(token)) {
                return SearchConnector.AND;
            }
            return OR_KEYWORD.equalsIgnoreCase(token) ? SearchConnector.OR : null;
        }
    }
}
