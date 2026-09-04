package com.centraldungeon.files.dto;

import com.centraldungeon.files.TableFileType;
import jakarta.validation.constraints.NotNull;

/**
 * Changing an attachment without touching the file: what it is for on this table, and who sees it.
 *
 * <p>Nothing here can reach the file itself. Sharing a map on one table says nothing about the same
 * map on another, and that separation is what {@code table_files} exists for (#79).
 *
 * @param tableFileType what the file is doing on the table: prepared beforehand, or produced at a
 *                      session
 * @param isPrivate     true to keep it to the people running this table, false to share it with its
 *                      players and candidates
 */
public record UpdateTableFileRequest(@NotNull TableFileType tableFileType, boolean isPrivate) {
}
