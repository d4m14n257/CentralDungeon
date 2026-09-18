package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.common.search.SearchQueryParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The vocabulary of the table's search box. Small on purpose: what it fixes is the wire contract the
 * frontend's {@code searchFields.ts} mirrors, and a rename on either side has to break something.
 *
 * <p>Six commands since F3.3. The language belongs to the <b>entity</b> and not to the screen (#239):
 * {@code /table_status} and {@code /table_master} were added for {@code /admin/tables} (#176), and the
 * explorer parses them too - where they can only ever narrow what the visibility rules already
 * allowed, which is the invariant {@code GameTableSearchSpecification} documents.
 */
class GameTableSearchFieldTest {

    @Test
    @DisplayName("every wire name carries its entity in front (#239)")
    void prefixesEveryWireNameWithTheEntity() {
        assertThat(GameTableSearchField.wireNames())
                .containsExactlyInAnyOrder(
                        "table_name", "table_system", "table_tag", "table_platform", "table_status",
                        "table_master");
    }

    @Test
    @DisplayName("the three catalog fields name their catalog, and the other three name none")
    void mapsEachFieldToItsCatalog() {
        assertThat(GameTableSearchField.NAME.catalog()).isNull();
        assertThat(GameTableSearchField.SYSTEM.catalog()).isEqualTo(CatalogType.SYSTEMS);
        assertThat(GameTableSearchField.TAG.catalog()).isEqualTo(CatalogType.TAGS);
        assertThat(GameTableSearchField.PLATFORM.catalog()).isEqualTo(CatalogType.PLATFORMS);
        assertThat(GameTableSearchField.STATUS.catalog()).isNull();
        assertThat(GameTableSearchField.MASTER.catalog()).isNull();
    }

    /**
     * The three ways a field is answered, told apart by two nullable accessors: a plain column, a
     * catalog resolved through synonym groups, and a subquery over another table. {@code /table_master}
     * is the third and the only one of its kind - it has no catalog <b>and</b> no attribute, which is
     * precisely what makes the specification route it to the {@code exists} over {@code masters}
     * rather than to a LIKE it could not build.
     */
    @Test
    @DisplayName("only the two plain columns carry a JPA attribute")
    void onlyThePlainColumnsCarryAnAttribute() {
        assertThat(GameTableSearchField.NAME.attribute()).isEqualTo("name");
        assertThat(GameTableSearchField.STATUS.attribute()).isEqualTo("status");
        assertThat(GameTableSearchField.MASTER.attribute()).isNull();
        assertThat(GameTableSearchField.SYSTEM.attribute()).isNull();
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
