package com.centraldungeon.tables.dto;

import com.centraldungeon.tables.MasterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMasterRequest(@NotBlank String userId, @NotNull MasterType masterType) {
}
