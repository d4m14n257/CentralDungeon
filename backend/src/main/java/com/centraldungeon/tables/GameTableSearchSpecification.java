package com.centraldungeon.tables;

import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.catalogs.TableCatalogLinkStatus;
import com.centraldungeon.catalogs.TablePlatform;
import com.centraldungeon.catalogs.TableSystem;
import com.centraldungeon.catalogs.TableTag;
import com.centraldungeon.common.search.SearchConnector;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchTerm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns the parsed explorer search box into a predicate, the same shape
 * {@code FileSearchSpecification} has for the files. Criteria API because what the query looks like
 * is only known at runtime: how many criteria and joined by which connectors (arquitectura.md 2.2).
 *
 * <p><b>The catalog criteria arrive already resolved.</b> Expanding {@code D&D} into its synonym
 * group needs the catalog tables, and a specification is built inside the query it belongs to - it
 * has no repository and should not grow one. So {@code GameTableService} resolves each catalog term
 * to a set of ids first (#54, #56, #246) and hands the map in; what is left here is the question
 * «does this table link to any of these», which is a subquery and nothing more.
 *
 * <p><b>A criterion that resolved to nothing matches nothing</b>, and that distinction is the one
 * that must not be lost: «no accepted value is called <em>Pathfimder</em>» is not «no filter». Read
 * the second way, a typo would list every table in the platform.
 */
final class GameTableSearchSpecification {

    /** Escape character of the LIKE patterns, so a literal % or _ is searched and not interpreted. */
    private static final char LIKE_ESCAPE = '\\';

    /** Utility class: it only builds specifications and holds no state. */
    private GameTableSearchSpecification() {
    }

    /**
     * The public explorer: what the actor may see, narrowed by what they typed.
     *
     * <p>The visibility rules and the search are joined with {@code and} and never folded into the
     * same expression: <b>a criterion the reader typed can only ever narrow what they were already
     * allowed to see</b>. Written any other way, an {@code or} between two criteria could reach
     * across the visibility filter and show a table the actor runs.
     *
     * @param query            the parsed search box; an empty one matches everything visible
     * @param catalogIdsByTerm the catalog ids each catalog criterion resolved to, keyed by the term
     *                         it came from. A term missing from the map, or mapped to an empty set,
     *                         matches no table
     * @param statuses         the statuses that count as publicly visible
     * @param actorId          the actor, from the token. It goes into the WHERE so a master never
     *                         sees their own table in the list meant for applying (#121, #154)
     * @return the predicate
     */
    static Specification<GameTable> forExplorer(
            SearchQuery query,
            Map<SearchTerm, Set<String>> catalogIdsByTerm,
            Collection<GameTableStatus> statuses,
            String actorId) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(statuses));
            predicates.add(notMasteredBy(root, criteriaQuery, builder, actorId));
            Predicate matched = matching(root, criteriaQuery, builder, query, catalogIdsByTerm);
            if (matched != null) {
                predicates.add(matched);
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * «the actor does not run this table» (#154).
     *
     * <p>Deliberately blind to {@code MasterRowStatus}, which is what the JPQL it replaces did too:
     * somebody who was removed as a co-master (#216) stopped running the table, so the table becomes
     * one they could apply to. Filtering by live rows here would keep it hidden from them forever.
     *
     * @param root          the table being queried
     * @param criteriaQuery the query being built, which owns the subquery
     * @param builder       the Criteria API builder
     * @param actorId       the actor, from the token
     * @return the predicate
     */
    private static Predicate notMasteredBy(
            Root<GameTable> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder builder, String actorId) {
        Subquery<String> mastered = criteriaQuery.subquery(String.class);
        Root<Master> master = mastered.from(Master.class);
        mastered.select(master.get("gameTable").get("id"));
        mastered.where(
                builder.equal(master.get("gameTable").get("id"), root.get("id")),
                builder.equal(master.get("user").get("id"), actorId));
        return builder.not(builder.exists(mastered));
    }

    /**
     * Folds the query's criteria into one predicate, left to right and without precedence.
     *
     * @param root             the entity being queried
     * @param criteriaQuery    the query being built, which owns the subqueries
     * @param builder          the Criteria API builder
     * @param query            the parsed search box
     * @param catalogIdsByTerm what each catalog criterion resolved to
     * @return the combined predicate, or null when the query is empty
     */
    private static @Nullable Predicate matching(
            Root<GameTable> root,
            CriteriaQuery<?> criteriaQuery,
            CriteriaBuilder builder,
            SearchQuery query,
            Map<SearchTerm, Set<String>> catalogIdsByTerm) {
        if (query.isEmpty()) {
            return null;
        }
        Predicate matched = null;
        for (SearchTerm term : query.terms()) {
            Predicate current = termPredicate(root, criteriaQuery, builder, term, catalogIdsByTerm);
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
     * One criterion, whichever kind it is.
     *
     * @param root             the entity being queried
     * @param criteriaQuery    the query being built
     * @param builder          the Criteria API builder
     * @param term             one criterion, with at least one value
     * @param catalogIdsByTerm what each catalog criterion resolved to
     * @return a predicate matching any of the criterion's values
     */
    private static Predicate termPredicate(
            Root<GameTable> root,
            CriteriaQuery<?> criteriaQuery,
            CriteriaBuilder builder,
            SearchTerm term,
            Map<SearchTerm, Set<String>> catalogIdsByTerm) {
        GameTableSearchField field = fieldOrDefault(term.field());
        CatalogType catalog = field.catalog();
        if (catalog != null) {
            return linkedToAny(root, criteriaQuery, builder, catalog, catalogIdsByTerm.getOrDefault(term, Set.of()));
        }
        Predicate matched = null;
        for (String value : term.values()) {
            Predicate current = contains(root, builder, field, value);
            matched = matched == null ? current : builder.or(matched, current);
        }
        return matched;
    }

    /**
     * «this table is linked to any of those catalog values», as a subquery over the bridge table.
     *
     * <p><b>A subquery and not a join, and that is the bug this shape exists to avoid.</b> A table
     * tagged with three values of the same group would come back three times from a join, so a page
     * of twenty rows would silently cover seventeen tables - and the total count would be wrong on
     * top of it. {@code exists} asks the same question without multiplying rows.
     *
     * <p>Only live links count: a value the master took off the table is kept as a row (#190) and is
     * not what the table is labelled with any more.
     *
     * @param root          the table being queried
     * @param criteriaQuery the query being built
     * @param builder       the Criteria API builder
     * @param catalog       which bridge table to look in
     * @param valueIds      the whole resolved synonym group; empty means the criterion matches nothing
     * @return the predicate
     */
    private static Predicate linkedToAny(
            Root<GameTable> root,
            CriteriaQuery<?> criteriaQuery,
            CriteriaBuilder builder,
            CatalogType catalog,
            Set<String> valueIds) {
        if (valueIds.isEmpty()) {
            return builder.disjunction();
        }
        Subquery<String> linked = criteriaQuery.subquery(String.class);
        Root<?> link = linked.from(bridgeOf(catalog));
        Path<String> tableId = link.get("id").get("gameTableId");
        Path<String> valueId = link.get("id").get(valueAttributeOf(catalog));
        linked.select(tableId);
        linked.where(
                builder.equal(tableId, root.get("id")),
                valueId.in(valueIds),
                builder.equal(link.get("status"), TableCatalogLinkStatus.Used));
        return builder.exists(linked);
    }

    /**
     * The bridge entity of one catalog.
     *
     * @param catalog the catalog
     * @return the entity that links a table to one of its values
     */
    private static Class<?> bridgeOf(CatalogType catalog) {
        return switch (catalog) {
            case SYSTEMS -> TableSystem.class;
            case TAGS -> TableTag.class;
            case PLATFORMS -> TablePlatform.class;
        };
    }

    /**
     * The name the bridge's composite key gives the catalog value.
     *
     * @param catalog the catalog
     * @return the attribute inside the embedded id that holds the value's id
     */
    private static String valueAttributeOf(CatalogType catalog) {
        return switch (catalog) {
            case SYSTEMS -> "systemId";
            case TAGS -> "tagId";
            case PLATFORMS -> "platformId";
        };
    }

    /**
     * A criterion with no field - or with one the parser did not recognize - falls back to the name.
     *
     * @param fieldName the wire name the criterion carried, or null when it carried none
     * @return the field to search, never null
     */
    private static GameTableSearchField fieldOrDefault(@Nullable String fieldName) {
        return Optional.ofNullable(fieldName)
                .flatMap(GameTableSearchField::fromWireName)
                .orElse(GameTableSearchField.NAME);
    }

    /**
     * A case-insensitive "contains" over one field.
     *
     * @param root    the entity being queried
     * @param builder the Criteria API builder
     * @param field   the field to match against; never a catalog one, those never reach here
     * @param value   the text to look for anywhere inside it
     * @return the LIKE predicate, with wildcards in the value escaped
     */
    private static Predicate contains(Root<GameTable> root, CriteriaBuilder builder, GameTableSearchField field, String value) {
        String attribute = field.attribute();
        if (attribute == null) {
            throw new IllegalStateException("Field " + field + " has no attribute and should have been resolved as a catalog");
        }
        Expression<String> column = builder.lower(root.get(attribute));
        return builder.like(column, "%" + escapeLikeWildcards(value.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE);
    }

    /**
     * Someone searching for "100% homebrew" is searching for that text, not for "anything".
     *
     * @param value the raw text the user typed
     * @return the same text with {@code \}, {@code %} and {@code _} escaped for LIKE
     */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
