package com.centraldungeon.catalogs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Proposing a value. Masters and admins can (#55); it is born in {@code Created} either way, and
 * shows to nobody until an admin accepts and classifies it (#57).
 *
 * @param name the value to add to the catalog. Surrounding whitespace is stripped by the service,
 *             and the name has to be free: proposing one that already exists answers 409 instead of
 *             creating a duplicate. Capped at 128 characters, the width of the column
 */
public record CreateCatalogValueRequest(@NotBlank @Size(max = 128) String name) {
}
