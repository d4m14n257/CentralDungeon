package com.centraldungeon.tables.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

public record TableStatusChangeResponse(
        String id, String fromStatus, String toStatus, String changedByName, @Nullable String justification, LocalDateTime createdAt) {
}
