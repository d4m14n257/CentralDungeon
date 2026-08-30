package com.centraldungeon.notifications;

import com.centraldungeon.notifications.dto.NotificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Wired as a @Bean in common/config/MapperConfig.java, not componentModel="spring" - see that class for why. */
@Mapper
public interface NotificationMapper {

    @Mapping(target = "notificationType", expression = "java(notification.getNotificationType().name())")
    @Mapping(target = "readStatus", expression = "java(notification.getReadStatus().name())")
    NotificationResponse toResponse(Notification notification);
}
