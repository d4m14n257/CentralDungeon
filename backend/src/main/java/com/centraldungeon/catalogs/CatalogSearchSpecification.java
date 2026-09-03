package com.centraldungeon.catalogs;

import com.centraldungeon.common.search.SearchConnector;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchTerm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns a parsed catalog search box into a predicate, the same way
 * {@code UserSearchSpecification} does for people. Criteria API because the shape is only known at
 * runtime: how many terms and joined by which connectors (arquitectura.md 2.2).
 *
 * <p>Two audiences, two entry points, and the difference between them is the whole point of #57:
 * {@link #accepted(SearchQuery)} is what a master's combobox and a player's filter see, and it never
 * returns a value that has not been accepted. {@link #forAdmin(SearchQuery, List)} sees everything,
 * because reviewing what was proposed is exactly the admin's job.
 */
final class CatalogSearchSpecification {

    /** Escape character of the LIKE patterns, so a literal % or _ is searched and not interpreted. */
    private static final char LIKE_ESCAPE = '\\';

    /** Utility class: it only builds specifications and holds no state. */
    private CatalogSearchSpecification() {
    }

    /**
     * Everything a non-admin is allowed to see: accepted values only (#57, #81).
     *
     * @param query the parsed search box; an empty one matches every accepted value
     * @param <E>   the catalog entity being queried
     * @return the predicate, visibility filter included
     */
    static <E extends CatalogValue> Specification<E> accepted(SearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            Predicate visible = builder.equal(root.get("status"), CatalogStatus.Accepted);
            Predicate matched = matching(root, builder, query);
            return matched == null ? visible : builder.and(visible, matched);
        };
    }

    /**
     * /admin/catalogs: no visibility filter, and an optional explicit status filter instead.
     *
     * @param query    the parsed search box; an empty one matches everything
     * @param statuses the statuses to keep, or empty for no status filter at all
     * @param <E>      the catalog entity being queried
     * @return the predicate, without any implicit visibility rule
     */
    static <E extends CatalogValue> Specification<E> forAdmin(SearchQuery query, List<CatalogStatus> statuses) {
        return (root, criteriaQuery, builder) -> {
            Predicate matched = matching(root, builder, query);
            if (statuses.isEmpty()) {
                return matched == null ? builder.conjunction() : matched;
            }
            Predicate byStatus = root.get("status").in(statuses);
            return matched == null ? byStatus : builder.and(byStatus, matched);
        };
    }

    /**
     * Folds the query's criteria into one predicate, left to right and without precedence.
     *
     * @param root    the entity being queried
     * @param builder the Criteria API builder
     * @param query   the parsed search box
     * @return the combined predicate, or null when the query is empty
     */
    private static @Nullable Predicate matching(Root<? extends CatalogValue> root, CriteriaBuilder builder, SearchQuery query) {
        if (query.isEmpty()) {
            return null;
        }
        Predicate matched = null;
        for (SearchTerm term : query.terms()) {
            Predicate current = termPredicate(root, builder, term);
            matched = matched == null ? current : combine(builder, matched, current, term.connector());
        }
        return matched;
    }

    /**
     * Joins two criteria with the connector the user wrote between them.
     *
     * @param builder   the Criteria API builder
     * @param left      everything accumulated so far
     * @param right     the criterion being added
     * @param connector how the user joined them
     * @return the joined predicate
     */
    private static Predicate combine(CriteriaBuilder builder, Predicate left, Predicate right, SearchConnector connector) {
        return connector == SearchConnector.OR ? builder.or(left, right) : builder.and(left, right);
    }

    /**
     * The values of one criterion are alternatives: any of them satisfies it (decisiones.md #164).
     *
     * @param root    the entity being queried
     * @param builder the Criteria API builder
     * @param term    one criterion, with at least one value
     * @return a predicate matching any of the criterion's values
     */
    private static Predicate termPredicate(Root<? extends CatalogValue> root, CriteriaBuilder builder, SearchTerm term) {
        Predicate matched = null;
        for (String value : term.values()) {
            Predicate current = contains(root, builder, fieldOrDefault(term.field()), value);
            matched = matched == null ? current : builder.or(matched, current);
        }
        return matched;
    }

    /**
     * A criterion with no field - or with one the parser did not recognize - falls back to the name.
     *
     * @param fieldName the wire name the criterion carried, or null when it carried none
     * @return the field to search, never null
     */
    private static CatalogSearchField fieldOrDefault(@Nullable String fieldName) {
        return Optional.ofNullable(fieldName)
                .flatMap(CatalogSearchField::fromWireName)
                .orElse(CatalogSearchField.NAME);
    }

    /**
     * A case-insensitive "contains" over one field.
     *
     * @param root    the entity being queried
     * @param builder the Criteria API builder
     * @param field   the field to match against
     * @param value   the text to look for anywhere inside it
     * @return the LIKE predicate, with wildcards in the value escaped
     */
    private static Predicate contains(
            Root<? extends CatalogValue> root, CriteriaBuilder builder, CatalogSearchField field, String value) {
        Expression<String> column = builder.lower(root.get(field.attribute()));
        return builder.like(column, "%" + escapeLikeWildcards(value.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE);
    }

    /**
     * Someone searching for "D&amp;D 100%" is searching for that text, not for "anything".
     *
     * @param value the raw text the user typed
     * @return the same text with {@code \}, {@code %} and {@code _} escaped for LIKE
     */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
