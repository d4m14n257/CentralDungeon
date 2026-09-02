package com.centraldungeon.common.search;

import org.jspecify.annotations.Nullable;

/**
 * One clause of a search query: a value, the field it applies to, and the connector that joins it
 * to whatever came before it. A null field means the default criterion of the endpoint - what the
 * user gets by typing without a {@code /field} prefix.
 *
 * <p>The connector of the first term of a query is meaningless and is always {@link SearchConnector#AND}.
 */
public record SearchTerm(@Nullable String field, String value, SearchConnector connector) {

    public static SearchTerm of(@Nullable String field, String value) {
        return new SearchTerm(field, value, SearchConnector.AND);
    }
}
