package com.centraldungeon.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SearchQueryParserTest {

    private static final Set<String> FIELDS = Set.of("discord_name", "user_name", "tag");

    @Test
    void plainTextIsASingleTermWithoutField() {
        SearchQuery query = SearchQueryParser.parse("juan", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, List.of("juan"), SearchConnector.AND));
    }

    @Test
    void everythingAfterAFieldIsItsValue() {
        SearchQuery query = SearchQueryParser.parse("/user_name juan pablo", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm("user_name", List.of("juan pablo"), SearchConnector.AND));
    }

    @Test
    void commasSplitOneCriterionIntoAlternatives() {
        SearchQuery query = SearchQueryParser.parse("/user_name damian,carlos, daniel", FIELDS);

        assertThat(query.terms())
                .containsExactly(new SearchTerm("user_name", List.of("damian", "carlos", "daniel"), SearchConnector.AND));
    }

    @Test
    void emptyPiecesBetweenCommasAreDropped() {
        SearchQuery query = SearchQueryParser.parse("damian, ,carlos,", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, List.of("damian", "carlos"), SearchConnector.AND));
    }

    @Test
    void joinsTwoCriteriaWithTheConnectorToken() {
        SearchQuery query = SearchQueryParser.parse("/user_name juan /or /discord_name pablo", FIELDS);

        assertThat(query.terms())
                .containsExactly(
                        new SearchTerm("user_name", List.of("juan"), SearchConnector.AND),
                        new SearchTerm("discord_name", List.of("pablo"), SearchConnector.OR));
    }

    @Test
    void defaultsToAndWhenNoConnectorIsWritten() {
        SearchQuery query = SearchQueryParser.parse("/user_name juan /discord_name pablo", FIELDS);

        assertThat(query.terms()).extracting(SearchTerm::connector).containsExactly(SearchConnector.AND, SearchConnector.AND);
    }

    @Test
    void connectorTokensAreCaseInsensitive() {
        SearchQuery query = SearchQueryParser.parse("juan /OR pablo", FIELDS);

        assertThat(query.terms()).extracting(SearchTerm::connector).containsExactly(SearchConnector.AND, SearchConnector.OR);
    }

    /** Without this nobody could search for a value containing the word: the separator is the slash. */
    @Test
    void aBareAndOrOrIsPlainText() {
        SearchQuery query = SearchQueryParser.parse("/user_name juan or pablo", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm("user_name", List.of("juan or pablo"), SearchConnector.AND));
    }

    /** A search box must survive being typed into, character by character. */
    @Test
    void dropsATrailingConnector() {
        SearchQuery query = SearchQueryParser.parse("juan /or", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, List.of("juan"), SearchConnector.AND));
    }

    @Test
    void ignoresAFieldWithNoValueYet() {
        assertThat(SearchQueryParser.parse("/user_name", FIELDS).isEmpty()).isTrue();
        assertThat(SearchQueryParser.parse("/user_name  ", FIELDS).isEmpty()).isTrue();
        assertThat(SearchQueryParser.parse("/user_name ,,", FIELDS).isEmpty()).isTrue();
    }

    @Test
    void anUnknownPrefixStaysLiteralTextInsteadOfFailing() {
        SearchQuery query = SearchQueryParser.parse("/nickname juan", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, List.of("/nickname juan"), SearchConnector.AND));
    }

    @Test
    void aLeadingConnectorJoinsNothingAndIsHarmless() {
        SearchQuery query = SearchQueryParser.parse("/or juan", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, List.of("juan"), SearchConnector.AND));
    }

    @Test
    void blankAndNullQueriesAreEmpty() {
        assertThat(SearchQueryParser.parse(null, FIELDS).isEmpty()).isTrue();
        assertThat(SearchQueryParser.parse("   ", FIELDS).isEmpty()).isTrue();
    }
}
