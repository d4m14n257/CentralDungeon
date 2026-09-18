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
    void cadaComandoSeResuelveDesdeSuNombreDeWire() {
        assertThat(ApprovalSearchField.fromWireName("request_type")).contains(ApprovalSearchField.REQUEST_TYPE);
        assertThat(ApprovalSearchField.fromWireName("STATUS")).contains(ApprovalSearchField.STATUS);
        assertThat(ApprovalSearchField.fromWireName("requested_by")).contains(ApprovalSearchField.REQUESTED_BY);
    }

    /** Un {@code /campo} que este endpoint no declara se busca como texto literal, nunca es un 400. */
    @Test
    void unComandoDesconocidoQuedaComoTexto() {
        assertThat(ApprovalSearchField.fromWireName("resolved_by")).isEmpty();

        SearchQuery query = SearchQueryParser.parse("/resolved_by damian", ApprovalSearchField.wireNames());

        assertThat(query.terms()).hasSize(1);
        assertThat(query.terms().getFirst().field()).isNull();
        assertThat(query.terms().getFirst().values()).containsExactly("/resolved_by damian");
    }

    @Test
    void elParserReconoceLosComandosDeclarados() {
        SearchQuery query = SearchQueryParser.parse("/status Pending /and /request_type MasterGrant", ApprovalSearchField.wireNames());

        assertThat(query.terms()).hasSize(2);
        assertThat(query.terms().getFirst().field()).isEqualTo("status");
        assertThat(query.terms().getFirst().values()).containsExactly("Pending");
        assertThat(query.terms().get(1).field()).isEqualTo("request_type");
        assertThat(query.terms().get(1).values()).containsExactly("MasterGrant");
    }

    /**
     * Los dos comandos de opciones fijas aceptan exactamente lo que la API publica, y lo resuelven
     * desde el enum y no desde una lista escrita aparte: una segunda lista es una que se desactualiza.
     */
    @Test
    void lasOpcionesFijasSalenDeLosEnums() {
        for (ApprovalRequestType type : ApprovalRequestType.values()) {
            assertThat(ApprovalRequestType.fromWireName(type.wireName())).contains(type);
        }
        for (ApprovalStatus status : ApprovalStatus.values()) {
            assertThat(ApprovalStatus.fromName(status.name())).contains(status);
        }
    }

    /** Un valor desconocido no es un 400: no matchea nada (arquitectura.md 2.5). */
    @Test
    void unValorDesconocidoNoEsUnError() {
        assertThat(ApprovalRequestType.fromWireName("TablePause")).isEmpty();
        assertThat(ApprovalStatus.fromName("Maybe")).isEmpty();
    }
}
