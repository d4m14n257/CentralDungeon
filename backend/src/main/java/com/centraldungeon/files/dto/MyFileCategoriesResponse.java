package com.centraldungeon.files.dto;

import java.util.List;

/**
 * The categories a person may file something of their own under.
 *
 * <p>It is a record and not a bare list so the body can grow without breaking the screen that reads it.
 *
 * @param categories the {@code FileCategory} names the actor may use, as strings.
 *                   <b>It can be empty</b>: an account holding only {@code Admin} or {@code Owner} goes
 *                   through none of the flows that fill a personal library
 */
public record MyFileCategoriesResponse(List<String> categories) {
}
