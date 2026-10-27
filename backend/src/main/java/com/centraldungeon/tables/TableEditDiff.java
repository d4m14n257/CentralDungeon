package com.centraldungeon.tables;

import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.tables.dto.UpdateGameTableRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which fields of a table a rewrite actually changes, as the stable codes an admin-edit notification
 * carries so the frontend can say «changed the name and the agenda» in the reader's language.
 *
 * <p>Computed <b>before</b> the rewrite touches the entity, from the values that will be stored: the
 * sanitized rich text, not what was typed, so a description that only differs by markup the
 * sanitizer strips is not reported as a change. The agenda is not here - the schedule service is
 * the one that knows whether it moved, and appends {@link #SCHEDULE}.
 */
final class TableEditDiff {

    /** Code for the table's title. */
    static final String NAME = "name";

    /** Code for the description. */
    static final String DESCRIPTION = "description";

    /** Code for the house rules. */
    static final String PERMITTED = "permitted";

    /** Code for the requirements. */
    static final String REQUIREMENTS = "requirements";

    /** Code for the start date. */
    static final String START_DATE = "startDate";

    /** Code for the planned session count. */
    static final String TOTAL_SESSIONS = "totalSessions";

    /** Code for the player cap. */
    static final String MAX_PLAYERS = "maxPlayers";

    /** Code for the table type. */
    static final String TABLE_TYPE = "tableType";

    /** Code for the game systems. */
    static final String SYSTEMS = "systems";

    /** Code for the tags. */
    static final String TAGS = "tags";

    /** Code for the platforms. */
    static final String PLATFORMS = "platforms";

    /** Code for the weekly agenda. */
    static final String SCHEDULE = "schedule";

    private TableEditDiff() {
    }

    /**
     * The codes of the fields that differ between the stored table and the values it is about to get.
     *
     * @param table          the table as it is stored now, not yet touched by the rewrite
     * @param request        the rewrite, whose {@code tableTypeId} and catalog ids are compared as sent
     * @param description    the description after sanitizing, the value that will be stored
     * @param permitted      the house rules after sanitizing
     * @param requirements   the requirements after sanitizing
     * @param maxPlayers     the player cap after the platform ceiling was applied
     * @param currentLinks   the table's live catalog links before the rewrite
     * @return the codes of what changed, in a stable order; empty when nothing did
     */
    static List<String> of(
            GameTable table,
            UpdateGameTableRequest request,
            @Nullable String description,
            @Nullable String permitted,
            @Nullable String requirements,
            @Nullable Integer maxPlayers,
            Map<CatalogType, List<CatalogValueResponse>> currentLinks) {
        List<String> changes = new ArrayList<>();
        addIf(changes, NAME, !Objects.equals(table.getName(), request.name()));
        addIf(changes, DESCRIPTION, textDiffers(table.getDescription(), description));
        addIf(changes, PERMITTED, textDiffers(table.getPermitted(), permitted));
        addIf(changes, REQUIREMENTS, textDiffers(table.getRequirements(), requirements));
        addIf(changes, START_DATE, !Objects.equals(table.getStartDate(), request.startDate()));
        addIf(changes, TOTAL_SESSIONS, !Objects.equals(table.getTotalSessions(), request.totalSessions()));
        addIf(changes, MAX_PLAYERS, !Objects.equals(table.getMaxPlayers(), maxPlayers));
        TableType type = table.getTableType();
        addIf(changes, TABLE_TYPE, !Objects.equals(type == null ? null : type.getId(), request.tableTypeId()));
        addIf(changes, SYSTEMS, idsDiffer(currentLinks.get(CatalogType.SYSTEMS), request.systemIds()));
        addIf(changes, TAGS, idsDiffer(currentLinks.get(CatalogType.TAGS), request.tagIds()));
        addIf(changes, PLATFORMS, idsDiffer(currentLinks.get(CatalogType.PLATFORMS), request.platformIds()));
        return changes;
    }

    private static void addIf(List<String> changes, String code, boolean changed) {
        if (changed) {
            changes.add(code);
        }
    }

    /** Null and empty are the same text: clearing a field that was never filled in changes nothing. */
    private static boolean textDiffers(@Nullable String stored, @Nullable String incoming) {
        return !Objects.equals(stored == null ? "" : stored, incoming == null ? "" : incoming);
    }

    /** Order and duplicates do not matter: a catalog is a set. */
    private static boolean idsDiffer(@Nullable List<CatalogValueResponse> stored, @Nullable List<String> incoming) {
        Set<String> before = new HashSet<>();
        if (stored != null) {
            stored.forEach(value -> before.add(value.id()));
        }
        return !before.equals(new HashSet<>(incoming == null ? List.of() : incoming));
    }
}
