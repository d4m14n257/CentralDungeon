package com.centraldungeon.tables.dto;

import org.jspecify.annotations.Nullable;

/**
 * One table type as the wizard's selector shows it.
 *
 * @param id          the type's identifier, what a table stores in {@code table_type_id}
 * @param code        the identifier the frontend translates by, for a type the application shipped
 *                    ({@code PUBLIC}, {@code FIRST_CLASS}). Null for one a person created, whose
 *                    {@code name} is then read verbatim (#225)
 * @param name        the type's label ("Public", "First class"). What a reader sees when there is no
 *                    code to translate
 * @param description what the type means. Null when the row never got one - "Public" alone does not
 *                    explain itself, so the selector shows this next to the name when it is there.
 *                    Like the name, it is a fallback: a coded type gets its description translated
 */
public record TableTypeResponse(String id, @Nullable String code, String name, @Nullable String description) {
}
