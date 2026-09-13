package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.common.search.SearchQueryParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The vocabulary of the explorer's search box. Small on purpose: what it fixes is the wire contract
 * the frontend's {@code searchFields.ts} mirrors, and a rename on either side has to break something.
 */
class GameTableSearchFieldTest {

    @Test
    @DisplayName("every wire name carries its entity in front (#239)")
    void prefixesEveryWireNameWithTheEntity() {
        assertThat(GameTableSearchField.wireNames())
                .containsExactlyInAnyOrder("table_name", "table_system", "table_tag", "table_platform");
    }

    @Test
    @DisplayName("the three catalog fields name their catalog, and the name field names none")
    void mapsEachFieldToItsCatalog() {
        assertThat(GameTableSearchField.NAME.catalog()).isNull();
        assertThat(GameTableSearchField.SYSTEM.catalog()).isEqualTo(CatalogType.SYSTEMS);
        assertThat(GameTableSearchField.TAG.catalog()).isEqualTo(CatalogType.TAGS);
        assertThat(GameTableSearchField.PLATFORM.catalog()).isEqualTo(CatalogType.PLATFORMS);
    }

    @Test
    @DisplayName("a wire name resolves whatever case it was typed in")
    void resolvesAWireNameIgnoringCase() {
        assertThat(GameTableSearchField.fromWireName("TABLE_TAG")).contains(GameTableSearchField.TAG);
        assertThat(GameTableSearchField.fromWireName("  table_system  ")).contains(GameTableSearchField.SYSTEM);
    }

    @Test
    @DisplayName("a command nobody defined is not a command")
    void doesNotResolveAnUnknownWireName() {
        assertThat(GameTableSearchField.fromWireName("tag")).isEmpty();
    }

    /**
     * The other half of «a typo searches for itself»: the parser only treats a slash token as a
     * command when the field set says so, so {@code /tag} - the shorthand somebody would reach for -
     * stays literal text and finds nothing rather than returning a 400.
     */
    @Test
    @DisplayName("an unknown slash token stays literal text instead of failing")
    void keepsAnUnknownSlashTokenAsText() {
        var parsed = SearchQueryParser.parse("/tag horror", GameTableSearchField.wireNames());

        assertThat(parsed.terms()).allSatisfy(term -> assertThat(term.field()).isNull());
    }
}
