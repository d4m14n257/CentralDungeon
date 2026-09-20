package com.centraldungeon.registrations;

import com.centraldungeon.registrations.dto.RegistrationFileResponse;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Wired as a @Bean in common/config/MapperConfig.java, not componentModel="spring" - see that class for why. */
@Mapper
public interface RegistrationMapper {

    /**
     * @param registration the application to describe
     * @param attachedFiles what the applicant attached (#60 uso 2), resolved by the caller - a
     *                      mapper never touches a repository (arquitectura.md 2.2). Empty when
     *                      nothing was attached
     * @return the application as its reader sees it
     */
    @Mapping(target = "gameTableId", source = "registration.gameTable.id")
    @Mapping(target = "gameTableName", source = "registration.gameTable.name")
    @Mapping(target = "userId", source = "registration.user.id")
    @Mapping(
            target = "userName",
            expression =
                    "java(registration.getUser().getName() != null ? registration.getUser().getName() : registration.getUser().getDiscordUsername())")
    @Mapping(target = "userKarma", source = "registration.user.karma")
    @Mapping(target = "status", expression = "java(registration.getStatus().name())")
    @Mapping(target = "rejectionJustification", ignore = true)
    @Mapping(target = "rejectionReasonCode", ignore = true)
    @Mapping(target = "attachedFiles", source = "attachedFiles")
    // The veto's three fields are filled by the service when there is a veto to describe, the same
    // way the two rejection fields are: the trail lives in `registration_status_changes` and a
    // mapper never touches a repository (arquitectura.md 2.2).
    @Mapping(target = "blockedByName", ignore = true)
    @Mapping(target = "blockedAt", ignore = true)
    @Mapping(target = "blockJustification", ignore = true)
    RegistrationResponse toResponse(TableRegistration registration, List<RegistrationFileResponse> attachedFiles);
}
