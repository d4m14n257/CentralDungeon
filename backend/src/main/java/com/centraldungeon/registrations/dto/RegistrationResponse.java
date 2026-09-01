package com.centraldungeon.registrations.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

public record RegistrationResponse(
        String id,
        String gameTableId,
        String gameTableName,
        String userId,
        String userName,
        int userKarma,
        String status,
        @Nullable String description,
        LocalDateTime createdAt,
        @Nullable String rejectionJustification) {
}
