package com.centraldungeon.common.search;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One criterion of a search query: a field, the values that satisfy it, and the connector that
 * joins it to whatever came before it. A null field means the default criterion of the endpoint -
 * what the user gets by typing without a {@code /field} prefix.
 *
 * <p><b>Several values are alternatives</b>: {@code /user_name damian,carlos} matches either one.
 * The list is never empty - a criterion with nothing to match is not a criterion, and the parser
 * drops it.
 *
 * <p>The connector of the first term of a query is meaningless and is always {@link SearchConnector#AND}.
 */
public record SearchTerm(@Nullable String field, List<String> values, SearchConnector connector) {

    public SearchTerm {
        values = List.copyOf(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("A search term needs at least one value");
        }
    }
}
