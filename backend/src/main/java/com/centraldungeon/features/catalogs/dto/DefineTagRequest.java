package com.centraldungeon.features.catalogs.dto;

import com.centraldungeon.features.catalogs.TagCategory;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * An admin saying what a tag group is: its category and its definition. Both replace what the group
 * had, so sending null clears them.
 *
 * @param category    the group's category, or null for none
 * @param description the group's definition in plain text, up to 500 characters, or null for none.
 *                    Blank counts as none. It is shown as text, never rendered as markup
 */
public record DefineTagRequest(@Nullable TagCategory category, @Nullable @Size(max = 500) String description) {
}
