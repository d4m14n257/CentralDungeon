package com.centraldungeon.tables;

/**
 * How many times one attendance value was recorded for somebody, on one particular table. Internal
 * projection of {@link SessionAttendanceRepository}, not a DTO: it never crosses HTTP -
 * {@code GameTableHistoryResponse} carries the aggregate that {@link AttendanceCount} already
 * reduces to.
 *
 * <p>It exists so a page of the player's history (#133) costs one grouped query for the whole page
 * rather than one {@link SessionAttendanceRepository#countByTableAndUser} per row - the same shape
 * {@code CatalogUsageCount} and {@code FileService.usagesByFileId} already hold for a page of
 * catalog values and a page of files.
 *
 * @param gameTableId the table this row's count belongs to
 * @param attendance  the value counted. {@code Unknown} never appears: the query leaves it out of
 *                    the denominator, which is the whole point of #137
 * @param total       how many sessions on this table were recorded with it for this person. A
 *                    value never recorded on this table is absent from the result, so a missing
 *                    entry means zero
 */
public record TableAttendanceCount(String gameTableId, AttendanceStatus attendance, long total) {
}
