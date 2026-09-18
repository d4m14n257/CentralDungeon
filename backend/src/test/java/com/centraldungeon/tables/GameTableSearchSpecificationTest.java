package com.centraldungeon.tables;

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
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The two commands F3.3 added to the table's search box, and the one structural rule they must not
 * break.
 *
 * <p><b>{@code /table_master} is an {@code exists} subquery and never a join.</b> That is not a taste
 * about SQL: a table with three masters comes back three times from a join, so a page of twenty rows
 * would silently cover fewer than twenty tables and {@code totalElements} would be wrong on top of it.
 * It is the same bug {@code linkedToAny} was written around for the catalogs, arriving through a new
 * door - and the only thing that can catch it before a user does is an assertion about which Criteria
 * calls get made.
 *
 * <p>The builder is mocked rather than run against a database, for the reason
 * {@code ApprovalSearchSpecificationTest} gives: what is being checked is which predicate gets
 * composed, which is a statement about this class and not about SQL.
 */
class GameTableSearchSpecificationTest {

    private static final Map<com.centraldungeon.common.search.SearchTerm, java.util.Set<String>> NO_CATALOGS =
            Map.of();

    private static final List<GameTableStatus> EVERY_LISTABLE_STATUS =
            List.of(GameTableStatus.Preparation, GameTableStatus.Opened);

    /**
     * The rule of #176 that a join would have quietly broken. {@code exists} is called; {@code join}
     * on the root is not.
     */
    @Test
    void tableMasterPreguntaConUnExistsYNuncaConUnJoin() {
        Criteria criteria = new Criteria();
        SearchQuery query = SearchQueryParser.parse("/table_master ana", GameTableSearchField.wireNames());

        GameTableSearchSpecification.forAdmin(query, NO_CATALOGS, EVERY_LISTABLE_STATUS)
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.builder).exists(any(Subquery.class));
        verify(criteria.root, never()).join(anyString());
        verify(criteria.root, never()).join(anyString(), any(JoinType.class));
    }

    /**
     * Por nombre y nunca por id, como todos los precedentes - y por los dos nombres, porque quien
     * busca conoce uno de los dos y no cuál guarda el sistema dónde.
     */
    @Test
    void tableMasterBuscaPorLosDosNombresDeLaPersona() {
        Criteria criteria = new Criteria();
        SearchQuery query = SearchQueryParser.parse("/table_master ana", GameTableSearchField.wireNames());

        GameTableSearchSpecification.forAdmin(query, NO_CATALOGS, EVERY_LISTABLE_STATUS)
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.masterUser).get("name");
        verify(criteria.masterUser).get("discordUsername");
    }

    /**
     * Solo masters vivos: a quien lo sacaron de co-master (#216) ya no dirige la mesa, y encontrarla
     * por su nombre sería responder una pregunta sobre el pasado. Es la decisión <b>opuesta</b> a la
     * de {@code notMasteredBy}, a propósito, y por eso se fija acá.
     */
    @Test
    void tableMasterSoloMiraLasFilasVivas() {
        Criteria criteria = new Criteria();
        SearchQuery query = SearchQueryParser.parse("/table_master ana", GameTableSearchField.wireNames());

        GameTableSearchSpecification.forAdmin(query, NO_CATALOGS, EVERY_LISTABLE_STATUS)
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.builder).equal(criteria.masterStatus, MasterRowStatus.Created);
    }

    /** {@code /table_status} es lista cerrada: matchea el enum, no un LIKE sobre el texto. */
    @Test
    void tableStatusEsUnaIgualdadSobreElEnum() {
        Criteria criteria = new Criteria();
        SearchQuery query = SearchQueryParser.parse("/table_status Preparation", GameTableSearchField.wireNames());

        GameTableSearchSpecification.forAdmin(query, NO_CATALOGS, EVERY_LISTABLE_STATUS)
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.builder).equal(criteria.status, GameTableStatus.Preparation);
        verify(criteria.builder, never()).like(any(), anyString(), any(Character.class));
    }

    /**
     * Un estado que no existe matchea <b>nada</b>, y nunca responde 400 (arquitectura.md §2.5). La
     * distinción que no se puede perder: «no hay ningún estado que se llame Abierta» no es «sin
     * filtro» - leído del segundo modo, un typo listaría toda la plataforma.
     */
    @Test
    void unEstadoQueNoExisteNoMatcheaNadaYNoEsUn400() {
        Criteria criteria = new Criteria();
        SearchQuery query = SearchQueryParser.parse("/table_status Abierta", GameTableSearchField.wireNames());

        GameTableSearchSpecification.forAdmin(query, NO_CATALOGS, EVERY_LISTABLE_STATUS)
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.builder).disjunction();
    }

    /**
     * {@code notMasteredBy} es del explorador y no va en el admin (contrato F3.3 §3.2): un admin no
     * está postulándose a nada, y esconderle las mesas que dirige sería un filtro que nadie pidió y
     * que nadie podría explicar.
     */
    @Test
    void elListadoAdminNoExcluyeLasMesasDeNadie() {
        Criteria criteria = new Criteria();

        GameTableSearchSpecification.forAdmin(SearchQuery.EMPTY, NO_CATALOGS, EVERY_LISTABLE_STATUS)
                .toPredicate(criteria.root, criteria.query, criteria.builder);

        verify(criteria.builder, never()).not(any(Predicate.class));
        verify(criteria.builder, never()).exists(any(Subquery.class));
    }

    /**
     * Y el filtro de estados siempre está: lo que se escriba en la caja acota lo que el listado ya
     * mostraba, nunca lo ensancha - una mesa borrada no se alcanza con ningún {@code ?q=} (#25).
     */
    @Test
    void laCajaAcotaLoQueElListadoYaMostraba() {
        Criteria criteria = new Criteria();
        SearchQuery query = SearchQueryParser.parse("cripta", GameTableSearchField.wireNames());

        assertThat(GameTableSearchSpecification.forAdmin(query, NO_CATALOGS, EVERY_LISTABLE_STATUS)
                        .toPredicate(criteria.root, criteria.query, criteria.builder))
                .isNotNull();

        verify(criteria.statusPath).in(EVERY_LISTABLE_STATUS);
        verify(criteria.builder).and(any(Predicate.class), any(Predicate.class));
    }

    /** The Criteria API, mocked down to the few calls these predicates make. */
    @SuppressWarnings("unchecked")
    private static final class Criteria {

        private final Root<GameTable> root = mock(Root.class);

        private final CriteriaQuery<?> query = mock(CriteriaQuery.class);

        private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

        /** {@code status} on the table itself - both the {@code in} of the listing and the equality of the command. */
        private final Path<Object> status = mock(Path.class);

        /** The same path, typed as what {@code root.get("status")} returns so {@code in(...)} can be verified. */
        private final Path<Object> statusPath;

        /** {@code masters.status} inside the subquery. */
        private final Path<Object> masterStatus = mock(Path.class);

        /** {@code masters.user} inside the subquery - where the two names are read from. */
        private final Path<Object> masterUser = mock(Path.class);

        private Criteria() {
            statusPath = status;
            when(root.get("status")).thenReturn(status);
            when(root.get("name")).thenReturn(mock(Path.class));
            when(root.get("id")).thenReturn(mock(Path.class));
            when(status.in(any(java.util.Collection.class))).thenReturn(mock(Predicate.class));

            Subquery<String> subquery = mock(Subquery.class);
            when(query.subquery(String.class)).thenReturn(subquery);
            Root<Master> master = mock(Root.class);
            when(subquery.from(Master.class)).thenReturn(master);
            Path<Object> gameTable = mock(Path.class);
            when(master.get("gameTable")).thenReturn(gameTable);
            when(gameTable.get("id")).thenReturn(mock(Path.class));
            when(master.get("status")).thenReturn(masterStatus);
            when(master.get("user")).thenReturn(masterUser);
            when(masterUser.get(anyString())).thenReturn(mock(Path.class));

            Join<Object, Object> join = mock(Join.class);
            when(root.join(anyString(), any(JoinType.class))).thenReturn(join);

            when(builder.lower(any())).thenReturn(mock(Expression.class));
            when(builder.like(any(), anyString(), any(Character.class))).thenReturn(mock(Predicate.class));
            // any(Object.class) and not a bare any(): CriteriaBuilder overloads equal() on
            // (Expression, Object) and (Expression, Expression), and an untyped matcher binds to the
            // second - leaving every real call, which passes a String or an enum, unstubbed.
            when(builder.equal(any(Expression.class), any(Object.class))).thenReturn(mock(Predicate.class));
            when(builder.and(any(Predicate.class), any(Predicate.class))).thenReturn(mock(Predicate.class));
            when(builder.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
            when(builder.or(any(Predicate.class), any(Predicate.class))).thenReturn(mock(Predicate.class));
            when(builder.not(any(Predicate.class))).thenReturn(mock(Predicate.class));
            when(builder.exists(any(Subquery.class))).thenReturn(mock(Predicate.class));
            when(builder.conjunction()).thenReturn(mock(Predicate.class));
            when(builder.disjunction()).thenReturn(mock(Predicate.class));
        }
    }
}
