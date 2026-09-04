package com.centraldungeon.dashboard.dto;

import java.util.List;

/**
 * Everything waiting for the master's answer, across every table they run (#136).
 *
 * <p>A record wrapping the list rather than a bare {@code List}: the tray is going to grow a
 * "generated at" or a count of tables scanned, and a top-level array has nowhere to put either.
 *
 * <p><b>An empty list is a success, not an absence.</b> It means every table is up to date, and the
 * screen says so in those words - a tray that reads as broken when there is no work is the trap
 * frontend-diseno.md 5 warns about by name.
 *
 * @param items what is waiting, longest wait first. Empty when nothing is
 */
public record MasterDashboardResponse(List<MasterWorkItem> items) {
}
