package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.catalogs.AbstractCatalogService;
import com.centraldungeon.catalogs.CatalogServices;
import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.common.search.SearchConnector;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.search.SearchTerm;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The step that turns «D&D» into a set of catalog ids (#54, #56, #246).
 *
 * <p>What it can prove without a database is the routing: which criteria go to which catalog, which
 * ones are left alone, and that the words of one criterion travel together. Whether the resulting
 * ids actually match the right tables is {@code GameTableSearchIT}'s to answer.
 */
@ExtendWith(MockitoExtension.class)
class GameTableSearchResolverTest {

    @Mock
    private CatalogServices catalogServices;

    @Mock
    private AbstractCatalogService<?> tagService;

    @Mock
    private AbstractCatalogService<?> systemService;

    private GameTableSearchResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GameTableSearchResolver(catalogServices);
    }

    /** Parses the way the service does, so the test exercises the real wire names and not a guess. */
    private static SearchQuery parse(String raw) {
        return SearchQueryParser.parse(raw, GameTableSearchField.wireNames());
    }

    @Test
    @DisplayName("a tag criterion is resolved against the tag catalog and nothing else")
    void resolvesATagCriterionAgainstTheTagCatalog() {
        doReturn(tagService).when(catalogServices).of(CatalogType.TAGS);
        when(tagService.resolveGroupIdsByName(anyCollection())).thenReturn(Set.of("tag-1", "tag-2"));

        Map<SearchTerm, Set<String>> resolved = resolver.resolveCatalogTerms(parse("/table_tag horror"));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.values()).containsExactly(Set.of("tag-1", "tag-2"));
    }

    @Test
    @DisplayName("the values of one criterion are resolved together, not one query each (#164)")
    void resolvesTheAlternativesOfOneCriterionInASingleCall() {
        doReturn(tagService).when(catalogServices).of(CatalogType.TAGS);
        when(tagService.resolveGroupIdsByName(anyCollection())).thenReturn(Set.of("tag-1"));

        resolver.resolveCatalogTerms(parse("/table_tag horror,misterio"));

        ArgumentCaptor<java.util.Collection<String>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(tagService).resolveGroupIdsByName(captor.capture());
        assertThat(captor.getValue()).containsExactly("horror", "misterio");
    }

    @Test
    @DisplayName("each catalog criterion goes to its own catalog")
    void routesEachCriterionToItsOwnCatalog() {
        doReturn(tagService).when(catalogServices).of(CatalogType.TAGS);
        doReturn(systemService).when(catalogServices).of(CatalogType.SYSTEMS);
        when(tagService.resolveGroupIdsByName(anyCollection())).thenReturn(Set.of("tag-1"));
        when(systemService.resolveGroupIdsByName(anyCollection())).thenReturn(Set.of("system-1"));

        Map<SearchTerm, Set<String>> resolved = resolver.resolveCatalogTerms(parse("/table_tag horror /table_system dnd"));

        assertThat(resolved).hasSize(2);
        assertThat(resolved.values()).containsExactlyInAnyOrder(Set.of("tag-1"), Set.of("system-1"));
    }

    @Test
    @DisplayName("the default criterion is the table's name and reaches no catalog")
    void leavesThePlainTextCriterionAlone() {
        Map<SearchTerm, Set<String>> resolved = resolver.resolveCatalogTerms(parse("dragones"));

        assertThat(resolved).isEmpty();
        verify(catalogServices, never()).of(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("/table_name is written out and still reaches no catalog")
    void leavesTheExplicitNameCriterionAlone() {
        Map<SearchTerm, Set<String>> resolved = resolver.resolveCatalogTerms(parse("/table_name dragones"));

        assertThat(resolved).isEmpty();
        verify(catalogServices, never()).of(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("an empty box resolves nothing")
    void resolvesNothingForAnEmptyBox() {
        assertThat(resolver.resolveCatalogTerms(SearchQuery.EMPTY)).isEmpty();
        verify(catalogServices, never()).of(org.mockito.ArgumentMatchers.any());
    }

    /**
     * The distinction the whole design turns on: «no accepted value is called that» is an answer, and
     * it has to come back as an empty set rather than as a missing entry. The specification reads a
     * missing entry and an empty set the same way - matches nothing - and this test fixes the first
     * half of that contract.
     */
    @Test
    @DisplayName("a word that names no accepted value resolves to the empty set, not to no criterion")
    void resolvesAnUnknownWordToAnEmptySet() {
        doReturn(tagService).when(catalogServices).of(CatalogType.TAGS);
        when(tagService.resolveGroupIdsByName(anyCollection())).thenReturn(Set.of());

        Map<SearchTerm, Set<String>> resolved = resolver.resolveCatalogTerms(parse("/table_tag pathfimder"));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.values()).containsExactly(Set.of());
    }

    @Test
    @DisplayName("two criteria that read the same share one resolution")
    void collapsesTwoIdenticalCriteria() {
        doReturn(tagService).when(catalogServices).of(CatalogType.TAGS);
        when(tagService.resolveGroupIdsByName(anyCollection())).thenReturn(Set.of("tag-1"));

        SearchTerm term = new SearchTerm("table_tag", List.of("horror"), SearchConnector.AND);
        Map<SearchTerm, Set<String>> resolved = resolver.resolveCatalogTerms(new SearchQuery(List.of(term, term)));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(term)).containsExactly("tag-1");
    }
}
