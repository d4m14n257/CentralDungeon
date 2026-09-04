package com.centraldungeon.files;

import com.centraldungeon.common.search.SearchConnector;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchTerm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns the parsed /admin/files search box into a predicate, the same shape
 * {@code CatalogSearchSpecification} has for the catalogs. Criteria API because what the query looks
 * like is only known at runtime: how many criteria and joined by which connectors (arquitectura.md
 * 2.2).
 *
 * <p>One entry point and not two, unlike the catalogs: files have no "proposed" state that has to be
 * hidden from everybody but an admin (#57). What is hidden here is what was marked gone, and that is
 * an explicit filter the screen chooses rather than an implicit rule.
 */
final class FileSearchSpecification {

    /** Escape character of the LIKE patterns, so a literal % or _ is searched and not interpreted. */
    private static final char LIKE_ESCAPE = '\\';

    /** Utility class: it only builds specifications and holds no state. */
    private FileSearchSpecification() {
    }

    /**
     * /admin/files: the search box, plus the filters the screen sets explicitly.
     *
     * @param query     the parsed search box; an empty one matches everything
     * @param statuses  the statuses to keep, or empty for no status filter at all
     * @param fileTypes the lifecycles to keep (#68), or empty for no type filter at all
     * @return the predicate
     */
    static Specification<StoredFile> forAdmin(SearchQuery query, List<FileStatus> statuses, List<FileType> fileTypes) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Predicate matched = matching(root, builder, query);
            if (matched != null) {
                predicates.add(matched);
            }
            if (!statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (!fileTypes.isEmpty()) {
                predicates.add(root.get("fileType").in(fileTypes));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
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
    private static @Nullable Predicate matching(Root<StoredFile> root, CriteriaBuilder builder, SearchQuery query) {
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
     * The values of one criterion are alternatives: any of them satisfies it (#164).
     *
     * @param root    the entity being queried
     * @param builder the Criteria API builder
     * @param term    one criterion, with at least one value
     * @return a predicate matching any of the criterion's values
     */
    private static Predicate termPredicate(Root<StoredFile> root, CriteriaBuilder builder, SearchTerm term) {
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
    private static FileSearchField fieldOrDefault(@Nullable String fieldName) {
        return Optional.ofNullable(fieldName).flatMap(FileSearchField::fromWireName).orElse(FileSearchField.NAME);
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
            Root<StoredFile> root, CriteriaBuilder builder, FileSearchField field, String value) {
        Expression<String> column = builder.lower(resolve(root, field.attribute()));
        return builder.like(column, "%" + escapeLikeWildcards(value.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE);
    }

    /**
     * Walks a dotted attribute path, so a field can live on an association.
     *
     * <p>{@code owner} maps to {@code userCreated.discordUsername}, and {@code root.get()} only takes
     * one segment at a time. The implicit join it produces is an inner one, which is right here:
     * {@code user_created_id} is {@code NOT NULL}, so no file can be lost by joining to its uploader.
     *
     * @param root      the entity being queried
     * @param attribute the attribute path, segments separated by dots
     * @return the expression the path resolves to
     */
    private static Path<String> resolve(Root<StoredFile> root, String attribute) {
        Path<?> path = root;
        for (String segment : attribute.split("\\.")) {
            path = path.get(segment);
        }
        @SuppressWarnings("unchecked")
        Path<String> resolved = (Path<String>) path;
        return resolved;
    }

    /**
     * Someone searching for "ficha 100%" is searching for that text, not for "anything".
     *
     * @param value the raw text the user typed
     * @return the same text with {@code \}, {@code %} and {@code _} escaped for LIKE
     */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
