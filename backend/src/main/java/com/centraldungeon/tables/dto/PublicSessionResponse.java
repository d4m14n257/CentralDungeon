package com.centraldungeon.tables.dto;

import com.centraldungeon.tables.TableSessionStatus;
import java.time.LocalDateTime;

/**
 * One session as anybody looking at the table sees it, on {@code /tables/:id}: when, and how it went.
 *
 * <p>It travels inside {@link GameTableDetailResponse} rather than on an endpoint of its own, and
 * that is the point: the table's detail already decides who may see the table at all - a vetoed
 * reader gets a {@code 404} there (#29) - so the calendar inherits that single answer instead of
 * repeating the check in a second place where it could drift.
 *
 * <p>Neither the notes nor anybody's attendance are here. Those belong to the people running the
 * table and to the person they are about ({@link TableSessionResponse}, {@link PlayerSessionResponse}).
 *
 * @param id             the session
 * @param sequenceNumber which session of the run this is, from 1
 * @param scheduledAt    when it happens, <b>in UTC</b> (#22)
 * @param status         planned, played or called off
 */
public record PublicSessionResponse(String id, int sequenceNumber, LocalDateTime scheduledAt, TableSessionStatus status) {
}
