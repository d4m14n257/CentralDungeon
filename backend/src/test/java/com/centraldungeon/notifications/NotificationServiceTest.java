package com.centraldungeon.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationMapper notificationMapper;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository, notificationMapper);
    }

    @Test
    void notifiesAnAcceptedRegistration() {
        User recipient = persistedUser("player-1");
        when(userRepository.getReferenceById("player-1")).thenReturn(recipient);
        GameTable table = persistedTable("table-1", "La Cripta");

        notificationService.notifyRegistrationAccepted("player-1", table);

        verify(notificationRepository).save(argThatMatchesType(NotificationType.RegistrationAccepted));
    }

    @Test
    void notifiesARejectedRegistrationWithTheJustificationAsTheMessage() {
        User recipient = persistedUser("player-2");
        when(userRepository.getReferenceById("player-2")).thenReturn(recipient);
        GameTable table = persistedTable("table-2", "Hijos del Vacio");

        notificationService.notifyRegistrationRejected("player-2", table, "Mesa llena");

        verify(notificationRepository).save(argThatMatchesType(NotificationType.RegistrationRejected));
    }

    @Test
    void notifiesAMasterOfANewCandidateWithTheApplicantNameInTheTitle() {
        User recipient = persistedUser("master-1");
        when(userRepository.getReferenceById("master-1")).thenReturn(recipient);
        GameTable table = persistedTable("table-3", "Tumbas de Sal");

        notificationService.notifyNewCandidate("master-1", table, "Beto");

        verify(notificationRepository)
                .save(org.mockito.ArgumentMatchers.argThat(notification -> notification.getNotificationType() == NotificationType.NewCandidate
                        && notification.getTitle().equals("Beto se postuló a Tumbas de Sal")
                        && notification.getMessage() == null));
    }

    @Test
    void markAsReadThrowsWhenTheNotificationDoesNotExist() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("missing", "someone")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAsReadRejectsSomeoneElsesNotification() {
        Notification notification = new Notification(persistedUser("owner"), NotificationType.RegistrationAccepted, "Title", null, null, null);
        ReflectionTestUtils.setField(notification, "id", "notif-1");
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead("notif-1", "someone-else")).isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void markAsReadFlipsReadStatusForTheOwner() {
        Notification notification = new Notification(persistedUser("owner"), NotificationType.RegistrationAccepted, "Title", null, null, null);
        ReflectionTestUtils.setField(notification, "id", "notif-2");
        when(notificationRepository.findById("notif-2")).thenReturn(Optional.of(notification));

        notificationService.markAsRead("notif-2", "owner");

        assertThat(notification.getReadStatus()).isEqualTo(ReadStatus.Read);
    }

    @Test
    void markAllAsReadFlipsEveryUnreadNotificationForThatUser() {
        Notification first = new Notification(persistedUser("owner"), NotificationType.RegistrationAccepted, "Title 1", null, null, null);
        Notification second = new Notification(persistedUser("owner"), NotificationType.NewCandidate, "Title 2", null, null, null);
        when(notificationRepository.findByUser_IdAndReadStatus("owner", ReadStatus.Unread)).thenReturn(java.util.List.of(first, second));

        notificationService.markAllAsRead("owner");

        assertThat(first.getReadStatus()).isEqualTo(ReadStatus.Read);
        assertThat(second.getReadStatus()).isEqualTo(ReadStatus.Read);
    }

    private Notification argThatMatchesType(NotificationType type) {
        return org.mockito.ArgumentMatchers.argThat(notification -> notification.getNotificationType() == type);
    }

    private User persistedUser(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private GameTable persistedTable(String id, String name) {
        GameTable table = new GameTable(name, persistedUser("creator-of-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        return table;
    }
}
