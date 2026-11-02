package com.centraldungeon.files.dto;

/**
 * A table that holds a live link to a file, as /admin/files names it: enough to read which it is and
 * to link to its admin page.
 *
 * @param id   the table's id, for the link to its admin page
 * @param name the table's name, which is how an admin recognises it
 */
public record AdminFileTableResponse(String id, String name) {
}
