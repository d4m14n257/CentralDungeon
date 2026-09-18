package com.centraldungeon.approvals;

import com.centraldungeon.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.approvals.dto.ApprovalRequestSummaryResponse;
import com.centraldungeon.users.User;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;

/** Wired as a @Bean in common/config/MapperConfig.java, not componentModel="spring" - see that class for why. */
@Mapper
public interface ApprovalMapper {

    /**
     * One row of the admin listing, and of "my requests".
     *
     * <p>Written by hand rather than generated: three of the seven fields are a person resolved to a
     * display name and two are enums flattened to strings, which is more mapping than there is
     * copying.
     *
     * @param request the request
     * @return the summary the listing renders
     */
    default ApprovalRequestSummaryResponse toSummaryResponse(ApprovalRequest request) {
        return new ApprovalRequestSummaryResponse(
                request.getId(),
                request.getRequestType().wireName(),
                request.getStatus().name(),
                displayNameOf(request.getRequestedBy()),
                request.getJustification(),
                displayNameOrNull(request.getClaimedBy()),
                request.getCreatedAt());
    }

    /**
     * One request in full - the detail, and what every mutating endpoint answers with.
     *
     * @param request the request
     * @return the detail, with both people resolved to display names and the polymorphic reference
     *         published as the two plain strings it is (#78)
     */
    default ApprovalRequestDetailResponse toDetailResponse(ApprovalRequest request) {
        return new ApprovalRequestDetailResponse(
                request.getId(),
                request.getRequestType().wireName(),
                request.getStatus().name(),
                request.getEntityType(),
                request.getEntityId(),
                displayNameOf(request.getRequestedBy()),
                request.getJustification(),
                displayNameOrNull(request.getClaimedBy()),
                displayNameOrNull(request.getResolvedBy()),
                request.getResolutionNote(),
                request.getResolvedAt(),
                request.getCreatedAt());
    }

    /** The screen shows a person, not an id - the same fallback {@code UserMapper} uses. */
    private static String displayNameOf(User user) {
        return user.getName() != null ? user.getName() : user.getDiscordUsername();
    }

    /** Null stays null: an unreserved item and an unresolved one have nobody to name. */
    private static @Nullable String displayNameOrNull(@Nullable User user) {
        return user == null ? null : displayNameOf(user);
    }
}
