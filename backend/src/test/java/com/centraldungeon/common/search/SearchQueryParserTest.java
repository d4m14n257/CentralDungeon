package com.centraldungeon.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SearchQueryParserTest {

    private static final Set<String> FIELDS = Set.of("discord_name", "user_name", "tag");

    @Test
    void plainTextIsASingleTermWithoutField() {
        SearchQuery query = SearchQueryParser.parse("juan", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, "juan", SearchConnector.AND));
    }

    @Test
    void keepsSpacesInsideTheValueOfATerm() {
        SearchQuery query = SearchQueryParser.parse("/user_name juan pablo", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm("user_name", "juan pablo", SearchConnector.AND));
    }

    @Test
    void readsAFieldPrefixIntoItsOwnTerm() {
        SearchQuery query = SearchQueryParser.parse("/discord_name Pablo#1234", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm("discord_name", "Pablo#1234", SearchConnector.AND));
    }

    @Test
    void joinsTwoFieldsWithTheExplicitConnector() {
        SearchQuery query = SearchQueryParser.parse("/user_name juan or /discord_name pablo", FIELDS);

        assertThat(query.terms())
                .containsExactly(
                        new SearchTerm("user_name", "juan", SearchConnector.AND),
                        new SearchTerm("discord_name", "pablo", SearchConnector.OR));
    }

    @Test
    void defaultsToAndWhenNoConnectorIsWritten() {
        SearchQuery query = SearchQueryParser.parse("/user_name juan /discord_name pablo", FIELDS);

        assertThat(query.terms()).extracting(SearchTerm::connector).containsExactly(SearchConnector.AND, SearchConnector.AND);
    }

    @Test
    void connectorsAreCaseInsensitive() {
        SearchQuery query = SearchQueryParser.parse("juan OR pablo", FIELDS);

        assertThat(query.terms()).extracting(SearchTerm::connector).containsExactly(SearchConnector.AND, SearchConnector.OR);
    }

    /** A search box must survive being typed into, character by character. */
    @Test
    void dropsATrailingConnector() {
        SearchQuery query = SearchQueryParser.parse("juan or", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, "juan", SearchConnector.AND));
    }

    @Test
    void ignoresAFieldWithNoValueYet() {
        assertThat(SearchQueryParser.parse("/user_name", FIELDS).isEmpty()).isTrue();
        assertThat(SearchQueryParser.parse("/user_name  ", FIELDS).isEmpty()).isTrue();
    }

    @Test
    void anUnknownPrefixStaysLiteralTextInsteadOfFailing() {
        SearchQuery query = SearchQueryParser.parse("/nickname juan", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, "/nickname juan", SearchConnector.AND));
    }

    @Test
    void aLeadingConnectorIsJustText() {
        SearchQuery query = SearchQueryParser.parse("or juan", FIELDS);

        assertThat(query.terms()).containsExactly(new SearchTerm(null, "or juan", SearchConnector.AND));
    }

    @Test
    void blankAndNullQueriesAreEmpty() {
        assertThat(SearchQueryParser.parse(null, FIELDS).isEmpty()).isTrue();
        assertThat(SearchQueryParser.parse("   ", FIELDS).isEmpty()).isTrue();
    }
}
