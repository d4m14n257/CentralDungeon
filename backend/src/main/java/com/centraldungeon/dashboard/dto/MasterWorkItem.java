package com.centraldungeon.dashboard.dto;

import com.centraldungeon.dashboard.MasterWorkItemKind;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One thing waiting for an answer, on one table.
 *
 * <p>It carries a code and its parameters, never a sentence (#197). The reader's language decides
 * how "three people have been waiting since Tuesday" is written, and that decision belongs to the
 * frontend - the same reason {@code NotificationParams} exists.
 *
 * @param tableId   the table the work belongs to, so the row can link straight at the tab that
 *                  resolves it
 * @param tableName the table's name, to group the tray by table without a second read
 * @param kind      what is waiting. The frontend picks the phrase from it
 * @param subject   the one detail that makes the row concrete - the task's title for an overdue
 *                  task, the session's date for an unrecorded one. Null when the kind has none:
 *                  a table in ChangesRequested is entirely described by its own name
 * @param count     how many of them. Always at least 1; it is 1 for the kinds that describe the
 *                  table itself rather than a set of rows
 * @param since     when the wait started, in UTC (#22). It is what orders the whole tray: urgency
 *                  here is time waited, not volume (#136)
 */
public record MasterWorkItem(
        String tableId,
        String tableName,
        MasterWorkItemKind kind,
        @Nullable String subject,
        int count,
        LocalDateTime since) {
}
