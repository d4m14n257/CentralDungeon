package com.centraldungeon.files.dto;

import com.centraldungeon.files.FileCategory;
import jakarta.validation.constraints.NotNull;

/**
 * Changing what a file of the platform's library is, while it is not published (#282).
 *
 * <p>One cajón and not a list, because it is what the upload list asks for each file - one select per
 * row - and what the edit dialog offers: a file of the library is one kind of document. It replaces
 * whatever the library offered the file as; a player-side membership the file carries from its history
 * is not the library's to touch (#233).
 *
 * @param category what the file is. Publishable, or the service refuses it
 */
public record UpdateLibraryCategoryRequest(@NotNull FileCategory category) {
}
