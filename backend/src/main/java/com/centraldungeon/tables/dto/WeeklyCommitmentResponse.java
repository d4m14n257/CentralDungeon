package com.centraldungeon.tables.dto;

import java.util.List;

/**
 * One table the reader's week is committed to, and the stretches it occupies (#227).
 *
 * <p>It is the same data #178 compares to refuse a clash, read instead of enforced: what the grid
 * draws is exactly what the rule measures, so a slot that looks free on screen is a slot the server
 * will accept.
 *
 * @param tableId    the table, so the block can link to it
 * @param tableName  its name, which is what the block is labelled with
 * @param role       {@code Master} when the reader runs it, {@code Player} when they play at it.
 *                   Running wins when somebody is both: it is the stronger claim on the evening
 * @param status     the table's status, so a draft can be told apart from a table already running
 * @param blocks     the stretches it occupies, in UTC minutes from Monday 00:00. <b>Empty</b> when
 *                   the table has no agenda or no duration yet — it commits nobody to anything
 *                   (#178), and the screen lists it apart rather than pretending it occupies a slot
 */
public record WeeklyCommitmentResponse(String tableId, String tableName, String role, String status, List<WeeklyBlockResponse> blocks) {
}
