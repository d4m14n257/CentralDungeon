package com.centraldungeon.tables.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Handing an Unassigned table its first masters. It is the transition that skips review entirely
 * and opens the table directly, because an admin already vouched for it by creating it (#72).
 *
 * @param primaryUserId    who runs the table. Exactly one, and it becomes the table's live Primary
 *                         (#73)
 * @param secondaryUserIds the co-masters, if any. Null and empty mean the same thing - a table with
 *                         a single master
 */
public record AssignMastersRequest(@NotBlank String primaryUserId, @Nullable List<String> secondaryUserIds) {
}
