package com.centraldungeon.registrations;

import com.centraldungeon.registrations.dto.RegistrationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Wired as a @Bean in common/config/MapperConfig.java, not componentModel="spring" - see that class for why. */
@Mapper
public interface RegistrationMapper {

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
    RegistrationResponse toResponse(TableRegistration registration);
}
