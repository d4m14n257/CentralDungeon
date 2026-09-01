package com.centraldungeon.tables.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record AssignMastersRequest(@NotBlank String primaryUserId, @Nullable List<String> secondaryUserIds) {
}
