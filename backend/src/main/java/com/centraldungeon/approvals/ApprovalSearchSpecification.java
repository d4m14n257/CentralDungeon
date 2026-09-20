package com.centraldungeon.approvals;

import com.centraldungeon.common.search.SearchConnector;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchTerm;
import com.centraldungeon.users.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns a parsed search box into a predicate over {@code approval_requests}. Criteria API and not a
 * derived query, for the reason {@code UserSearchSpecification} gives: the shape is only known at
 * runtime - how many criteria, over which fields, joined by which connectors (arquitectura.md 2.2).
 *
 * <p>Connectors fold left to right with no precedence, so {@code a or b and c} is
 * {@code (a or b) and c} - which is how the chip row reads on screen (#164).
 *
 * <p><b>Two audiences, two entry points</b>, the same shape {@code UserSearchSpecification} uses for
 * the same reason. {@link #forAdmin(SearchQuery)} has no implicit filter at all: an empty query lists
 * every request in every state, like every other listing of the application, and the screen opening
 * on {@code Pending} is the frontend putting that in its initial {@code ?q=} - an endpoint with a
 * hidden default filter is one whose answer nobody can predict from its URL. {@link #mine(SearchQuery,
 * String)} forces {@code requested_by = the actor} and <b>nothing can turn that off</b>. The
 * predicate builder underneath is shared, so the two cannot drift apart.
 */
final class ApprovalSearchSpecification {

    private static final char LIKE_ESCAPE = '\\';

    private ApprovalSearchSpecification() {
    }

    /**
     * {@code /admin/requests}: no implicit filter, because seeing everybody's requests is the entire
     * point of the screen.
     *
     * <p><b>{@code PlayerBan} is listed here, and that is deliberate</b> - it is the one place it is.
     * F3.4 took it out of {@code /admin/queue} because the tray is work waiting on <em>you</em> and a
     * veto is the table's {@code Primary}'s to answer (#39). This screen is not a tray: it is the
     * record of every request there is, and an admin being able to see that a veto is in progress on
     * some table is exactly what a record is for. Seeing is not resolving -
     * {@code ApprovalService.requireMayResolve} answers a {@code 403 NOT_PRIMARY_MASTER} to an admin
     * who tries, which is the line between the two.
     *
     * @param query the parsed search box; an empty one matches every request
     * @return the predicate
     */
    static Specification<ApprovalRequest> forAdmin(SearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            Predicate matched = matching(root, builder, query);
            return matched == null ? builder.conjunction() : matched;
        };
    }

    /**
     * {@code /requests/mine}: the actor's own requests, and only ever those.
     *
     * <p><b>The requester filter is forced and is not part of the query language.</b> It is applied
     * here rather than by translating some {@code /requested_by} into the actor, because a filter the
     * caller can express is a filter the caller can express differently: the whole reason this
     * endpoint cannot leak somebody else's requests is that no string arriving over HTTP reaches this
     * predicate. The id comes from the token and from nowhere else (#121, arquitectura.md 2.6).
     *
     * <p>The rest of the language works exactly as it does for an admin, which is the point of
     * routing it through the same builder. The screen needs {@code /status Pending} to be able to ask
     * "do I already have one of these open?" without paging through months of resolved requests to
     * find out - and a button offered because the answer fell off page one is a button whose only
     * possible reply is a 409.
     *
     * @param query   the parsed search box; an empty one matches every request <em>of the actor's</em>
     * @param actorId the actor, from the token. Never from a parameter
     * @return the predicate, requester filter included
     */
    static Specification<ApprovalRequest> mine(SearchQuery query, String actorId) {
        return (root, criteriaQuery, builder) -> {
            // Not negotiable and not reachable from outside: every path into this listing is the
            // actor's own, whatever the query says.
            Predicate own = builder.equal(root.get("requestedBy").get("id"), actorId);
            Predicate matched = matching(root, builder, query);
            return matched == null ? own : builder.and(own, matched);
        };
    }

    /** Folds the query's criteria into one predicate, left to right and without precedence. */
    private static @Nullable Predicate matching(
            Root<ApprovalRequest> root, CriteriaBuilder builder, SearchQuery query) {
        if (query.isEmpty()) {
            return null;
        }
        Joins joins = new Joins();
        Predicate matched = null;
        for (SearchTerm term : query.terms()) {
            Predicate current = termPredicate(root, builder, joins, term);
            matched = matched == null ? current : combine(builder, matched, current, term.connector());
        }
        return matched;
    }

    /**
     * One join to {@code users} per query, however many criteria need it.
     *
     * <p>Without it, {@code /requested_by ana /or /requested_by luis} emits two identical joins.
     * Neither would be wrong - {@code requested_by} is {@code NOT NULL} with a real foreign key, so a
     * join to it can neither drop a row nor multiply one - but the SQL stops being readable, which is
     * the first thing anybody looks at when a search feels slow. Created on demand, so a query of
     * {@code /status Pending} alone joins nothing.
     */
    private static final class Joins {

        private @Nullable Path<User> requester;

        private Path<User> requester(Root<ApprovalRequest> root) {
            if (requester == null) {
                requester = root.join("requestedBy", JoinType.INNER);
            }
            return requester;
        }
    }

    private static Predicate combine(CriteriaBuilder builder, Predicate left, Predicate right, SearchConnector connector) {
        return connector == SearchConnector.OR ? builder.or(left, right) : builder.and(left, right);
    }

    /** The values of one criterion are alternatives: any of them satisfies it (#164). */
    private static Predicate termPredicate(
            Root<ApprovalRequest> root, CriteriaBuilder builder, Joins joins, SearchTerm term) {
        Predicate matched = null;
        for (String value : term.values()) {
            Predicate current = valuePredicate(root, builder, joins, term.field(), value);
            matched = matched == null ? current : builder.or(matched, current);
        }
        return matched;
    }

    /**
     * A criterion with no field - or with one the parser did not recognize - falls back to the
     * justification <em>and</em> the requester's two names.
     */
    private static Predicate valuePredicate(
            Root<ApprovalRequest> root,
            CriteriaBuilder builder,
            Joins joins,
            @Nullable String fieldName,
            String value) {
        Optional<ApprovalSearchField> field = Optional.ofNullable(fieldName).flatMap(ApprovalSearchField::fromWireName);
        if (field.isEmpty()) {
            return builder.or(
                    contains(builder, root.get("justification"), value), requesterNamed(root, builder, joins, value));
        }
        return switch (field.get()) {
            case REQUEST_TYPE -> hasRequestType(root, builder, value);
            case STATUS -> hasStatus(root, builder, value);
            case REQUESTED_BY -> requesterNamed(root, builder, joins, value);
        };
    }

    /** {@code /request_type MasterGrant}: an equality, and an unknown type matches nothing rather than answering 400. */
    private static Predicate hasRequestType(Root<ApprovalRequest> root, CriteriaBuilder builder, String value) {
        return ApprovalRequestType.fromWireName(value)
                .map(type -> builder.equal(root.get("requestType"), type))
                .orElseGet(builder::disjunction);
    }

    /** {@code /status Pending}: an equality, and an unknown status matches nothing. */
    private static Predicate hasStatus(Root<ApprovalRequest> root, CriteriaBuilder builder, String value) {
        return ApprovalStatus.fromName(value)
                .map(status -> builder.equal(root.get("status"), status))
                .orElseGet(builder::disjunction);
    }

    /**
     * {@code /requested_by carla}, and also what bare text falls back to.
     *
     * <p>Both of the requester's names, because whoever is searching knows one of the two and not
     * which one the system stores where - the same reasoning {@code UserSearchField} gives for its
     * own default.
     *
     * <p>An inner join, and it is not a limitation: {@code requested_by} is {@code NOT NULL} with a
     * real foreign key, so every row has exactly one requester and the join can neither drop a row
     * nor multiply one.
     */
    private static Predicate requesterNamed(
            Root<ApprovalRequest> root, CriteriaBuilder builder, Joins joins, String value) {
        Path<User> requester = joins.requester(root);
        return builder.or(
                contains(builder, requester.get("name"), value),
                contains(builder, requester.get("discordUsername"), value));
    }

    private static Predicate contains(CriteriaBuilder builder, Path<String> column, String value) {
        Expression<String> lowered = builder.lower(column);
        return builder.like(lowered, "%" + escapeLikeWildcards(value.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE);
    }

    /** Somebody typing "100%" is searching for that text, not for "anything" (#124 in spirit: never build SQL by hand). */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
