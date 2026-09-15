package com.centraldungeon.users;

import com.centraldungeon.users.dto.AdminUserDetailResponse;
import com.centraldungeon.users.dto.AdminUserSummaryResponse;
import com.centraldungeon.users.dto.UserAdminChangeResponse;
import com.centraldungeon.users.dto.UserDetailResponse;
import com.centraldungeon.users.dto.UserSummaryResponse;
import java.util.Collection;
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

    /**
     * One row of {@code /admin/users}.
     *
     * <p>Written by hand rather than generated because the roles arrive as a separate collection -
     * {@code User} does not map them (#122) - and because their order is part of the contract:
     * {@link PlatformRole#ordered} is what keeps the chips from reshuffling between two rows.
     *
     * @param user  the person
     * @param roles the role names they hold right now, in any order
     * @return the summary the admin listing renders
     */
    default AdminUserSummaryResponse toAdminSummaryResponse(User user, Collection<String> roles) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getDiscordUsername(),
                user.getName(),
                user.getCountry(),
                user.getStatus().name(),
                PlatformRole.ordered(roles),
                user.getCreatedAt());
    }

    /**
     * The detail dialog of {@code /admin/users}, and what all six mutating endpoints answer with.
     *
     * @param user  the person
     * @param roles the role names they hold right now, in any order
     * @return the detail the admin dialog renders
     */
    default AdminUserDetailResponse toAdminDetailResponse(User user, Collection<String> roles) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getDiscordUsername(),
                user.getName(),
                user.getCountry(),
                user.getStatus().name(),
                PlatformRole.ordered(roles),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    /**
     * One role change, as the history panel lists it.
     *
     * @param change the audit row
     * @return the entry, with the actor already resolved to a display name
     */
    default UserAdminChangeResponse toAdminChangeResponse(UserRoleChange change) {
        return new UserAdminChangeResponse(
                change.getId(),
                change.getAction() == UserRoleChangeAction.Granted ? "RoleGranted" : "RoleRevoked",
                change.getRole().getName(),
                null,
                null,
                displayNameOf(change.getChangedBy()),
                change.getJustification(),
                change.getCreatedAt());
    }

    /**
     * One account status change, as the history panel lists it.
     *
     * @param change the audit row
     * @return the entry, with the actor already resolved to a display name
     */
    default UserAdminChangeResponse toAdminChangeResponse(UserStatusChange change) {
        return new UserAdminChangeResponse(
                change.getId(),
                "StatusChanged",
                null,
                change.getFromStatus().name(),
                change.getToStatus().name(),
                displayNameOf(change.getChangedBy()),
                change.getJustification(),
                change.getCreatedAt());
    }

    /** The screen shows a person, not an id - the same fallback {@code TableStatusChangeResponse} uses. */
    private static String displayNameOf(User user) {
        return user.getName() != null ? user.getName() : user.getDiscordUsername();
    }
}
