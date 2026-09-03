package com.centraldungeon.catalogs;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code {type}} path segment of /admin/catalogs to {@link CatalogType}. Spring's built-in
 * enum converter matches constant names exactly, and the path segment is lowercase plural
 * ({@code /admin/catalogs/tags}), so the mapping is declared here rather than bent into the enum's
 * constant names.
 */
@Component
public class CatalogTypeConverter implements Converter<String, CatalogType> {

    /**
     * Resolves a path segment to its catalog.
     *
     * @param source the segment as it arrived in the URL
     * @return the matching catalog
     * @throws IllegalArgumentException if the segment names no catalog. {@code GlobalExceptionHandler}
     *                                  turns the resulting type mismatch into a 400 - never the 500
     *                                  an unhandled one would produce
     */
    @Override
    public CatalogType convert(String source) {
        return CatalogType.fromWireName(source)
                .orElseThrow(() -> new IllegalArgumentException("Unknown catalog type '" + source + "'"));
    }
}
