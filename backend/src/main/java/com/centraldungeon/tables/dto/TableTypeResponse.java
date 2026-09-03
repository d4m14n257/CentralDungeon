package com.centraldungeon.tables.dto;

import org.jspecify.annotations.Nullable;

/**
 * One table type as the wizard's selector shows it.
 *
 * @param id          the type's identifier, what a table stores in {@code table_type_id}
 * @param name        the type's label ("Public", "First class")
 * @param description what the type means. Null when the row never got one - "Public" alone does not
 *                    explain itself, so the selector shows this next to the name when it is there
 */
public record TableTypeResponse(String id, String name, @Nullable String description) {
}
