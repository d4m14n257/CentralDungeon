package com.centraldungeon.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** What the purge asks the repository to delete: the retention margin, and the count it reports back. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenPurgeServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void asksToDeleteRowsExpiredLongerThanTheMarginAndReturnsTheCount() {
        when(refreshTokenRepository.deleteStale(any(), any())).thenReturn(3);
        LocalDateTime before = LocalDateTime.now();

        int deleted = new RefreshTokenPurgeService(refreshTokenRepository).purgeStaleTokens();

        ArgumentCaptor<LocalDateTime> expiredBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository).deleteStale(expiredBefore.capture(), now.capture());
        assertThat(deleted).isEqualTo(3);
        assertThat(now.getValue()).isAfterOrEqualTo(before);
        assertThat(expiredBefore.getValue()).isEqualTo(now.getValue().minus(RefreshTokenPurgeService.RETENTION_AFTER_EXPIRY));
    }
}
