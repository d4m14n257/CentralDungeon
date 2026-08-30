package com.centraldungeon.users.dto;

import java.util.Set;
import org.jspecify.annotations.Nullable;

public record UserDetailResponse(
        String id, @Nullable String name, @Nullable String country, int karma, boolean needsOnboarding, Set<String> roles) {
}
