package com.centraldungeon.catalogs;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Which of the three catalogs an /admin/catalogs request is about. The wire name is the path
 * segment: {@code /api/v1/admin/catalogs/tags/...}.
 *
 * <p>One admin controller with a typed path variable, instead of three near-identical controllers:
 * the six admin operations (accept, reject, merge, split, disable, restore) are the same operation
 * on the same shape, and duplicating them three times would mean fixing every bug three times. The
 * authorization is still declared on each concrete method (arquitectura.md 2.4, last line).
 */
public enum CatalogType {

    /** Game systems - table {@code systems}, handled by {@code SystemService}. */
    SYSTEMS("systems", "System"),

    /** Free-form labels - table {@code tags}, handled by {@code TagService}. */
    TAGS("tags", "Tag"),

    /** Where a table is played - table {@code platforms}, handled by {@code PlatformService}. */
    PLATFORMS("platforms", "Platform");

    /** The path segment that selects this catalog, lowercase and plural. */
    private final String wireName;

    /** The catalog's name in the singular, for error messages. */
    private final String singular;

    /**
     * @param wireName the path segment that selects this catalog
     * @param singular how to name one of its values in a message
     */
    CatalogType(String wireName, String singular) {
        this.wireName = wireName;
        this.singular = singular;
    }

    /**
     * Returns the path segment that selects this catalog.
     *
     * @return the wire name, lowercase and plural
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Returns the catalog's name in the singular. Used to build error messages that name what was
     * not found instead of saying "value".
     *
     * @return the singular label, capitalized
     */
    public String singular() {
        return singular;
    }

    /**
     * Resolves a path segment to its catalog, case-insensitively.
     *
     * @param wireName the segment as it arrived in the URL
     * @return the matching catalog, or empty when the segment names no catalog
     */
    public static Optional<CatalogType> fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.wireName.equals(normalized)).findFirst();
    }
}
