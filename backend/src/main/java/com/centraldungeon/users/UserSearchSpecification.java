package com.centraldungeon.users;

import com.centraldungeon.common.search.SearchConnector;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchTerm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns a parsed search box into a predicate. Criteria API and not a derived query or a fixed
 * {@code @Query} because the shape is only known at runtime: how many terms, over which fields,
 * joined by which connectors (arquitectura.md 2.2).
 *
 * <p>Connectors are folded left to right, no precedence - {@code a or b and c} is {@code (a or b) and c},
 * which is what the chip row reads like on screen (decisiones.md #164).
 */
final class UserSearchSpecification {

    private static final char LIKE_ESCAPE = '\\';

    private UserSearchSpecification() {
    }

    static Specification<User> matching(SearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            // Blocked and Deleted people are never offered: nobody should be picked as a master here.
            Predicate visible = builder.equal(root.get("status"), UserStatus.Allowed);
            if (query.isEmpty()) {
                return visible;
            }
            Predicate matched = null;
            for (SearchTerm term : query.terms()) {
                Predicate current = termPredicate(root, builder, term);
                matched = matched == null ? current : combine(builder, matched, current, term.connector());
            }
            return builder.and(visible, matched);
        };
    }

    private static Predicate combine(CriteriaBuilder builder, Predicate left, Predicate right, SearchConnector connector) {
        return connector == SearchConnector.OR ? builder.or(left, right) : builder.and(left, right);
    }

    /** The values of one criterion are alternatives: any of them satisfies it (decisiones.md #164). */
    private static Predicate termPredicate(Root<User> root, CriteriaBuilder builder, SearchTerm term) {
        Predicate matched = null;
        for (String value : term.values()) {
            Predicate current = valuePredicate(root, builder, term.field(), value);
            matched = matched == null ? current : builder.or(matched, current);
        }
        return matched;
    }

    /** A criterion with no field - or with one the parser did not recognize - falls back to both names. */
    private static Predicate valuePredicate(Root<User> root, CriteriaBuilder builder, @Nullable String fieldName, String value) {
        Optional<UserSearchField> field = Optional.ofNullable(fieldName).flatMap(UserSearchField::fromWireName);
        if (field.isPresent()) {
            return contains(root, builder, field.get(), value);
        }
        return builder.or(
                contains(root, builder, UserSearchField.DISCORD_NAME, value),
                contains(root, builder, UserSearchField.USER_NAME, value));
    }

    private static Predicate contains(Root<User> root, CriteriaBuilder builder, UserSearchField field, String value) {
        Expression<String> column = builder.lower(root.get(field.attribute()));
        return builder.like(column, "%" + escapeLikeWildcards(value.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE);
    }

    /** A user typing "100%" is searching for that text, not for "anything" (#124 in spirit: never build SQL by hand). */
    private static String escapeLikeWildcards(@Nullable String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
