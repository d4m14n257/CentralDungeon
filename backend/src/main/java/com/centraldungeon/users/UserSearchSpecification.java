package com.centraldungeon.users;

import com.centraldungeon.common.search.SearchConnector;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchTerm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
 *
 * <p><b>Two audiences, two entry points</b>, the same shape {@code CatalogSearchSpecification} uses
 * for the same reason. {@link #matching(SearchQuery)} is the picker's: it forces
 * {@code status = Allowed} and nothing can turn that off, because nobody should ever be offered as
 * a master or a player while blocked. {@link #forAdmin(SearchQuery)} drops the filter, because
 * seeing a blocked account is the entire point of {@code /admin/users} - an admin who cannot find
 * the person they blocked cannot unblock them. The visibility rule is the only difference; the
 * predicate builder underneath is shared, so the two cannot drift apart.
 */
final class UserSearchSpecification {

    private static final char LIKE_ESCAPE = '\\';

    private UserSearchSpecification() {
    }

    /**
     * The picker's view: Allowed people only, whatever the query says.
     *
     * @param query the parsed search box; an empty one lists every Allowed person
     * @return the predicate, visibility filter included
     */
    static Specification<User> matching(SearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            // Blocked and Deleted people are never offered: nobody should be picked as a master here.
            Predicate visible = builder.equal(root.get("status"), UserStatus.Allowed);
            Predicate matched = matching(root, criteriaQuery, builder, query);
            return matched == null ? visible : builder.and(visible, matched);
        };
    }

    /**
     * {@code /admin/users}: no implicit visibility filter. A blocked account is found here or it is
     * found nowhere, and {@code /status} is how the admin narrows it down explicitly.
     *
     * @param query the parsed search box; an empty one lists everybody
     * @return the predicate, without any implicit status rule
     */
    static Specification<User> forAdmin(SearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            Predicate matched = matching(root, criteriaQuery, builder, query);
            return matched == null ? builder.conjunction() : matched;
        };
    }

    /** Folds the query's criteria into one predicate, left to right and without precedence. */
    private static @Nullable Predicate matching(
            Root<User> root, @Nullable CriteriaQuery<?> criteriaQuery, CriteriaBuilder builder, SearchQuery query) {
        if (query.isEmpty()) {
            return null;
        }
        Predicate matched = null;
        for (SearchTerm term : query.terms()) {
            Predicate current = termPredicate(root, criteriaQuery, builder, term);
            matched = matched == null ? current : combine(builder, matched, current, term.connector());
        }
        return matched;
    }

    private static Predicate combine(CriteriaBuilder builder, Predicate left, Predicate right, SearchConnector connector) {
        return connector == SearchConnector.OR ? builder.or(left, right) : builder.and(left, right);
    }

    /** The values of one criterion are alternatives: any of them satisfies it (decisiones.md #164). */
    private static Predicate termPredicate(
            Root<User> root, @Nullable CriteriaQuery<?> criteriaQuery, CriteriaBuilder builder, SearchTerm term) {
        Predicate matched = null;
        for (String value : term.values()) {
            Predicate current = valuePredicate(root, criteriaQuery, builder, term.field(), value);
            matched = matched == null ? current : builder.or(matched, current);
        }
        return matched;
    }

    /** A criterion with no field - or with one the parser did not recognize - falls back to both names. */
    private static Predicate valuePredicate(
            Root<User> root,
            @Nullable CriteriaQuery<?> criteriaQuery,
            CriteriaBuilder builder,
            @Nullable String fieldName,
            String value) {
        Optional<UserSearchField> field = Optional.ofNullable(fieldName).flatMap(UserSearchField::fromWireName);
        if (field.isEmpty()) {
            return builder.or(
                    contains(root, builder, UserSearchField.DISCORD_NAME, value),
                    contains(root, builder, UserSearchField.USER_NAME, value));
        }
        return switch (field.get()) {
            case ROLE -> holdsRole(root, criteriaQuery, builder, value);
            case STATUS -> hasStatus(root, builder, value);
            case DISCORD_NAME, USER_NAME -> contains(root, builder, field.get(), value);
        };
    }

    /**
     * {@code /role Admin}: the person holds that role and the grant is live.
     *
     * <p>An EXISTS and not a join, because {@code User} deliberately does not map its roles - they
     * are rows of {@code users_roles} read on demand (#122), and a join would multiply the page's
     * rows for anyone holding two roles.
     *
     * <p>A role name outside the four matches nothing rather than answering 400: an unknown value is
     * a search that finds nobody, which is what a search box being typed into needs (arquitectura.md
     * 2.5).
     */
    private static Predicate holdsRole(
            Root<User> root, @Nullable CriteriaQuery<?> criteriaQuery, CriteriaBuilder builder, String value) {
        Optional<PlatformRole> role = PlatformRole.fromRoleName(value);
        if (role.isEmpty() || criteriaQuery == null) {
            return builder.disjunction();
        }
        Subquery<String> grants = criteriaQuery.subquery(String.class);
        Root<UserRole> grant = grants.from(UserRole.class);
        grants.select(grant.get("id").get("userId"));
        grants.where(builder.and(
                builder.equal(grant.get("user").get("id"), root.get("id")),
                builder.equal(grant.get("role").get("name"), role.get().roleName()),
                builder.equal(grant.get("status"), UserRoleStatus.Allowed)));
        return builder.exists(grants);
    }

    /** {@code /status Blocked}: an equality, and an unknown status matches nothing. */
    private static Predicate hasStatus(Root<User> root, CriteriaBuilder builder, String value) {
        for (UserStatus status : UserStatus.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return builder.equal(root.get("status"), status);
            }
        }
        return builder.disjunction();
    }

    private static Predicate contains(Root<User> root, CriteriaBuilder builder, UserSearchField field, String value) {
        String attribute = field.attribute();
        if (attribute == null) {
            throw new IllegalStateException("Field " + field + " is not a substring match - it must not reach contains()");
        }
        Expression<String> column = builder.lower(root.get(attribute));
        return builder.like(column, "%" + escapeLikeWildcards(value.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE);
    }

    /** A user typing "100%" is searching for that text, not for "anything" (#124 in spirit: never build SQL by hand). */
    private static String escapeLikeWildcards(@Nullable String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
