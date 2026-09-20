package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

/**
 * The one rule of this class that is a security property rather than a convenience: <b>{@code mine}
 * scopes to the actor and nothing can turn that off</b>.
 *
 * <p>It is asserted here, against the Criteria API itself, rather than through the service, because
 * "the service passed some specification to the repository" would keep passing if the filter were
 * dropped. What has to be true is that the predicate that comes out names the id the caller was given
 * and never one that arrived inside {@code ?q=} - which is the whole reason
 * {@code GET /requests/mine} cannot be turned into a way of reading somebody else's requests (#121).
 *
 * <p>The builder is mocked rather than run against a database: what is being checked is which
 * predicate gets composed, and that is a statement about this class, not about SQL.
 */
class ApprovalSearchSpecificationTest {

    /** {@code /requests/mine} with an empty box: the actor's filter is the whole predicate. */
    @Test
    void myRequestsAreScopedToTheActorEvenWithNoSearch() {
        Criteria criteria = new Criteria();

        ApprovalSearchSpecification.mine(SearchQuery.EMPTY, "user-1")
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.builder).equal(criteria.requesterId, "user-1");
    }

    /**
     * And with a search, the actor's filter is combined with {@code AND}: whatever the box says narrows
     * nunca menos.
     */
    @Test
    void theSearchNarrowsMoreAndNeverLess() {
        Criteria criteria = new Criteria();
        SearchQuery query = SearchQueryParser.parse("/status Pending", ApprovalSearchField.wireNames());

        ApprovalSearchSpecification.mine(query, "user-1")
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.builder).equal(criteria.requesterId, "user-1");
        verify(criteria.builder).and(any(Predicate.class), any(Predicate.class));
        verify(criteria.builder, never()).or(any(Predicate.class), any(Predicate.class));
    }

    /**
     * The direct attempt: naming somebody else through the box. {@code /requested_by} still works -it is
     * the same language the admin listing speaks- but it is added to the actor's filter instead of
     * replacing it, so the only thing it can achieve is finding less.
     */
    @Test
    void nombrarAOtraPersonaEnLaCajaNoEnsanchaElResultado() {
        Criteria criteria = new Criteria();
        SearchQuery query = SearchQueryParser.parse("/requested_by otra-persona", ApprovalSearchField.wireNames());

        ApprovalSearchSpecification.mine(query, "user-1")
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        // The actor's id is still there, and it is the token's: no string from the request reaches it.
        verify(criteria.builder).equal(criteria.requesterId, "user-1");
        verify(criteria.builder, never()).equal(any(Expression.class), eq((Object) "otra-persona"));
    }

    /** {@code /admin/requests} carries no implicit filter: seeing everybody's requests is the screen. */
    @Test
    void theAdminListingScopesToNobody() {
        Criteria criteria = new Criteria();

        ApprovalSearchSpecification.forAdmin(SearchQuery.EMPTY)
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.builder, never()).equal(any(Expression.class), any(Object.class));
        verify(criteria.builder).conjunction();
    }

    /**
     * <b>And it does not narrow by type either</b>: a {@code PlayerBan} <em>is</em> visible in
     * {@code /admin/requests} even though it is not in {@code /admin/queue}. They are two different
     * questions - the tray is work waiting on you, and this is the record of every request there is.
     * Seeing is not resolving: an admin who tries to resolve one is answered
     * {@code 403 NOT_PRIMARY_MASTER} by {@code ApprovalService} (#39).
     */
    @Test
    void theAdminListingDoesNotNarrowByTypeEitherAndShowsVetoRequests() {
        Criteria criteria = new Criteria();

        ApprovalSearchSpecification.forAdmin(SearchQuery.EMPTY)
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.root, never()).get("requestType");
        verify(criteria.builder, never()).not(any(Predicate.class));
    }

    @Test
    void anEmptySearchByTheActorIsNotAnError() {
        Criteria criteria = new Criteria();

        assertThat(ApprovalSearchSpecification.mine(SearchQuery.EMPTY, "user-1")
                        .toPredicate(criteria.root, criteria.query, criteria.builder))
                .isNotNull();
    }

    /** The Criteria API, mocked down to the few calls these predicates make. */
    @SuppressWarnings("unchecked")
    private static final class Criteria {

        private final Root<ApprovalRequest> root = mock(Root.class);

        private final CriteriaQuery<?> query = mock(CriteriaQuery.class);

        private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

        /** {@code requestedBy.id} - the path the forced filter has to land on. */
        private final Path<Object> requesterId = mock(Path.class);

        private Criteria() {
            Path<Object> requestedBy = mock(Path.class);
            when(root.get("requestedBy")).thenReturn(requestedBy);
            when(requestedBy.get("id")).thenReturn(requesterId);
            when(root.get(anyString())).thenReturn(mock(Path.class));
            when(root.get("requestedBy")).thenReturn(requestedBy);

            Join<Object, Object> join = mock(Join.class);
            when(root.join("requestedBy", JoinType.INNER)).thenReturn(join);
            when(join.get(anyString())).thenReturn(mock(Path.class));

            when(builder.lower(any())).thenReturn(mock(Expression.class));
            when(builder.like(any(), anyString(), any(Character.class))).thenReturn(mock(Predicate.class));
            // any(Object.class) and not a bare any(): CriteriaBuilder overloads equal() on
            // (Expression, Object) and (Expression, Expression), and an untyped matcher binds to the
            // second - leaving every real call, which passes a String or an enum, unstubbed and
            // answering null.
            when(builder.equal(any(Expression.class), any(Object.class))).thenReturn(mock(Predicate.class));
            when(builder.and(any(Predicate.class), any(Predicate.class))).thenReturn(mock(Predicate.class));
            when(builder.or(any(Predicate.class), any(Predicate.class))).thenReturn(mock(Predicate.class));
            when(builder.conjunction()).thenReturn(mock(Predicate.class));
            when(builder.disjunction()).thenReturn(mock(Predicate.class));
        }
    }
}
