package com.centraldungeon.common.search;

import java.util.List;

/** A parsed search box, in the order the user wrote it. */
public record SearchQuery(List<SearchTerm> terms) {

    public static final SearchQuery EMPTY = new SearchQuery(List.of());

    public SearchQuery {
        terms = List.copyOf(terms);
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }
}
