package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import org.junit.jupiter.api.Test;

/**
 * The vocabulary of the admin request search (contrato F3.2, 2).
 *
 * <p>It is tested at the level of the parser rather than of the SQL because the interesting rules are
 * about what counts as a command and what counts as text: the frontend declares the same three names
 * in its own {@code searchFields.ts}, and the two lists agreeing is what makes a chip typed on screen
 * mean the same thing on this side.
 */
class ApprovalSearchFieldTest {

    @Test
    void aceptaLosTresComandosDelContratoYNadaMas() {
        assertThat(ApprovalSearchField.wireNames()).containsExactlyInAnyOrder("request_type", "status", "requested_by");
    }

    @Test
    void everyCommandResolvesFromItsWireName() {
        assertThat(ApprovalSearchField.fromWireName("request_type")).contains(ApprovalSearchField.REQUEST_TYPE);
        assertThat(ApprovalSearchField.fromWireName("STATUS")).contains(ApprovalSearchField.STATUS);
        assertThat(ApprovalSearchField.fromWireName("requested_by")).contains(ApprovalSearchField.REQUESTED_BY);
    }

    /** A {@code /command} this endpoint does not declare is searched as literal text, never a 400. */
    @Test
    void anUnknownCommandStaysAsText() {
        assertThat(ApprovalSearchField.fromWireName("resolved_by")).isEmpty();

        SearchQuery query = SearchQueryParser.parse("/resolved_by damian", ApprovalSearchField.wireNames());

        assertThat(query.terms()).hasSize(1);
        assertThat(query.terms().getFirst().field()).isNull();
        assertThat(query.terms().getFirst().values()).containsExactly("/resolved_by damian");
    }

    @Test
    void theParserRecognisesTheDeclaredCommands() {
        SearchQuery query = SearchQueryParser.parse("/status Pending /and /request_type MasterGrant", ApprovalSearchField.wireNames());

        assertThat(query.terms()).hasSize(2);
        assertThat(query.terms().getFirst().field()).isEqualTo("status");
        assertThat(query.terms().getFirst().values()).containsExactly("Pending");
        assertThat(query.terms().get(1).field()).isEqualTo("request_type");
        assertThat(query.terms().get(1).values()).containsExactly("MasterGrant");
    }

    /**
     * The two fixed-option commands accept exactly what the API publishes, and resolve it from the enum
     * rather than from a list written separately: a second list is a list that goes out of date.
     */
    @Test
    void theFixedOptionsComeFromTheEnums() {
        for (ApprovalRequestType type : ApprovalRequestType.values()) {
            assertThat(ApprovalRequestType.fromWireName(type.wireName())).contains(type);
        }
        for (ApprovalStatus status : ApprovalStatus.values()) {
            assertThat(ApprovalStatus.fromName(status.name())).contains(status);
        }
    }

    /** Un valor desconocido no es un 400: no matchea nada (arquitectura.md 2.5). */
    @Test
    void anUnknownValueIsNotAnError() {
        assertThat(ApprovalRequestType.fromWireName("Quizas")).isEmpty();
        assertThat(ApprovalStatus.fromName("Maybe")).isEmpty();
    }
}
