package com.centraldungeon.features.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.features.catalogs.CatalogType;
import com.centraldungeon.features.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.features.tables.dto.TableSections;
import com.centraldungeon.features.tables.dto.UpdateGameTableRequest;
import com.centraldungeon.features.users.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Which fields a rewrite changes, as the codes an admin-edit notification carries. */
class TableEditDiffTest {

    private static final Map<CatalogType, List<CatalogValueResponse>> LINKS = Map.of(
            CatalogType.SYSTEMS, List.of(value("s1"), value("s2")),
            CatalogType.TAGS, List.of(value("t1")),
            CatalogType.PLATFORMS, List.of(value("p1")));

    @Test
    void reportsEveryFieldThatDiffersAndOnlyThose() {
        GameTable table = table();
        UpdateGameTableRequest request = request("Renamed", List.of("s1", "s2"), List.of("t2"), List.of("p1"), LocalDate.parse("2026-12-01"));

        List<String> changes = TableEditDiff.of(table, request, "Different", "Rules", null, 5, LINKS, TableSections.EMPTY);

        assertThat(changes).containsExactly("name", "description", "startDate", "maxPlayers", "tags");
    }

    @Test
    void aCatalogIsASetSoOrderAndDuplicatesDoNotCount() {
        GameTable table = table();
        UpdateGameTableRequest request = request("Table", List.of("s2", "s1", "s1"), List.of("t1"), List.of("p1"), null);

        assertThat(TableEditDiff.of(table, request, "Description", "Rules", null, 4, LINKS, TableSections.EMPTY)).isEmpty();
    }

    @Test
    void nullAndEmptyTextAreTheSameText() {
        GameTable table = table();
        table.setRequirements("");
        UpdateGameTableRequest request = request("Table", List.of("s1", "s2"), List.of("t1"), List.of("p1"), null);

        assertThat(TableEditDiff.of(table, request, "Description", "Rules", null, 4, LINKS, TableSections.EMPTY)).isEmpty();
    }

    /** The four sections are reported as one change, whichever of them moved. */
    @Test
    void anyOfTheSectionsChangingIsReportedOnceAsSections() {
        GameTable table = table();
        table.setSafety("<p>Lines and veils</p>");
        UpdateGameTableRequest request = request("Table", List.of("s1", "s2"), List.of("t1"), List.of("p1"), null);

        List<String> changes = TableEditDiff.of(
                table, request, "Description", "Rules", null, 4, LINKS,
                new TableSections("<p>Point buy</p>", null, null, "<p>X card</p>"));

        assertThat(changes).containsExactly("sections");
    }

    /** Sections that stay as they are, empty or written, are not a change. */
    @Test
    void unchangedSectionsAreNotReported() {
        GameTable table = table();
        table.setEquipment("<p>Dice</p>");
        UpdateGameTableRequest request = request("Table", List.of("s1", "s2"), List.of("t1"), List.of("p1"), null);

        assertThat(TableEditDiff.of(
                        table, request, "Description", "Rules", null, 4, LINKS,
                        new TableSections("", null, "<p>Dice</p>", null)))
                .isEmpty();
    }

    private static GameTable table() {
        GameTable table = new GameTable("Table", new User("discord-1", "creator"));
        table.setDescription("Description");
        table.setPermitted("Rules");
        table.setMaxPlayers(4);
        return table;
    }

    private static UpdateGameTableRequest request(
            String name, List<String> systems, List<String> tags, List<String> platforms, LocalDate startDate) {
        return new UpdateGameTableRequest(name, null, null, null, null, systems, tags, platforms, startDate, null, null, List.of(), null);
    }

    private static CatalogValueResponse value(String id) {
        return new CatalogValueResponse(id, id, "Accepted", null, null);
    }
}
