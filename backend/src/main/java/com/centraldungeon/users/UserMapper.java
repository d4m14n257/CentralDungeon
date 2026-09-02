package com.centraldungeon.users;

import com.centraldungeon.users.dto.UserDetailResponse;
import com.centraldungeon.users.dto.UserSummaryResponse;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Wired as a @Bean in common/config/MapperConfig.java, not componentModel="spring" - see that class for why. */
@Mapper
public interface UserMapper {

    @Mapping(target = "needsOnboarding", expression = "java(!user.hasCompletedOnboarding())")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "hasManagedTables", source = "hasManagedTables")
    UserDetailResponse toDetailResponse(User user, Set<String> roles, boolean hasManagedTables);

    UserSummaryResponse toSummaryResponse(User user);
}
