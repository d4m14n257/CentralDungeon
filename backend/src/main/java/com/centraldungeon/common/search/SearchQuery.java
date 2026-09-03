package com.centraldungeon.common.search;

import java.util.List;

/**
 * A parsed search box, in the order the user wrote it.
 *
 * @param terms the criteria, left to right. Copied defensively, so the query cannot change under the
 *              specification that is reading it. Empty means "no criteria", which every endpoint
 *              reads as "everything you are allowed to see"
 */
public record SearchQuery(List<SearchTerm> terms) {

    /** The query of an empty search box. Shared, since the record is immutable. */
    public static final SearchQuery EMPTY = new SearchQuery(List.of());

    /** Copies the term list so the query stays immutable. */
    public SearchQuery {
        terms = List.copyOf(terms);
    }

    /**
     * Tells whether the box had nothing usable in it.
     *
     * @return true when there are no criteria to apply
     */
    public boolean isEmpty() {
        return terms.isEmpty();
    }
}
