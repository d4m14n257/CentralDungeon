package com.centraldungeon.tables;

import com.centraldungeon.catalogs.CatalogServices;
import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchTerm;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The step between parsing the explorer's search box and querying it: every catalog criterion is
 * expanded to the ids of its whole synonym group (#54, #56, #246).
 *
 * <p><b>It exists because a specification cannot ask the database a question of its own.</b>
 * {@code GameTableSearchSpecification} is a lambda that runs while a query is being built; giving it
 * a repository so it could resolve {@code D&D} into three ids would mean issuing queries from inside
 * the construction of another one. So the resolution happens first, here, and the specification
 * receives an answer instead of a question.
 *
 * <p><b>One query per catalog criterion, not one per value.</b> {@code /table_tag horror,misterio} is
 * a single resolution: the values of one criterion are alternatives (#164), so they seed one search
 * and come back as one group set.
 *
 * <p>A term whose text names no accepted value resolves to the empty set, and the specification reads
 * that as «matches no table». Not as «no filter» - that would turn a typo into a listing of
 * everything.
 */
@Service
public class GameTableSearchResolver {

    /** Resolves each {@link CatalogType} to the service that owns its groups. */
    private final CatalogServices catalogServices;

    /**
     * @param catalogServices resolves a catalog to the service that can expand its synonym groups
     */
    public GameTableSearchResolver(CatalogServices catalogServices) {
        this.catalogServices = catalogServices;
    }

    /**
     * Expands every catalog criterion of a parsed query.
     *
     * <p>The map is keyed by the term itself, which is safe because {@link SearchTerm} is a record:
     * two criteria that read the same resolve the same, so a collision costs one lookup and changes
     * no answer.
     *
     * @param query the parsed search box
     * @return the resolved ids of each catalog criterion. Criteria that are not about a catalog are
     *         absent, because there is nothing to resolve about them
     */
    @Transactional(readOnly = true)
    public Map<SearchTerm, Set<String>> resolveCatalogTerms(SearchQuery query) {
        Map<SearchTerm, Set<String>> resolved = new HashMap<>();
        for (SearchTerm term : query.terms()) {
            String fieldName = term.field();
            if (fieldName == null) {
                continue;
            }
            GameTableSearchField.fromWireName(fieldName)
                    .map(GameTableSearchField::catalog)
                    .ifPresent(catalog -> resolved.put(term, idsOf(catalog, term)));
        }
        return resolved;
    }

    /**
     * The whole synonym group behind one criterion's words.
     *
     * @param catalog which catalog to resolve against
     * @param term    the criterion, whose values are alternatives
     * @return the accepted ids of every group the words landed on, possibly empty
     */
    private Set<String> idsOf(CatalogType catalog, SearchTerm term) {
        return catalogServices.of(catalog).resolveGroupIdsByName(term.values());
    }
}
