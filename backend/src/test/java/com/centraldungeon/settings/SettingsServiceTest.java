package com.centraldungeon.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ApiException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.settings.dto.SystemSettingResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.util.unit.DataSize;

/**
 * The rules of {@code /admin/settings} (#141): where a value comes from when nobody has changed it,
 * what happens when somebody does, and what the API refuses.
 *
 * <p>The three that carry the weight of the slice:
 *
 * <ul>
 *   <li><b>No row means the shipped default.</b> {@code system_settings} holds overrides only, so a
 *       fresh database and a wiped table behave identically and no seed has to be kept in step with
 *       {@link SettingKey}.</li>
 *   <li><b>The range is checked in the service and not in the record.</b> Bean Validation cannot see
 *       which key the URL named, so a bound written as {@code @Max} would hold for one caller and not
 *       for the others.</li>
 *   <li><b>A change and its audit row are one act.</b> A platform-wide adjustment produces no
 *       notification and no visible event; without the row the only trace is a number that used to be
 *       different.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SystemSettingRepository settingRepository;

    @Mock
    private SystemSettingChangeRepository changeRepository;

    @Mock
    private UserService userService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private SettingsService service;

    private final User actorUser = new User("discord-admin", "the-admin");

    private final CurrentUser actor = new CurrentUser("admin-1", Set.of("Admin"));

    @BeforeEach
    void setUp() {
        service = new SettingsService(
                settingRepository, changeRepository, userService, cacheManager, DataSize.ofMegabytes(26));

        lenient().when(cacheManager.getCache("systemSettings")).thenReturn(cache);
        lenient().when(userService.getById("admin-1")).thenReturn(actorUser);
        lenient().when(settingRepository.save(any(SystemSetting.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------------------------------------------------------------- reading

    /** Nothing overridden: every accessor answers with what {@link SettingKey} ships. */
    @Test
    void anUnsetSettingAnswersWithItsShippedDefault() {
        when(settingRepository.findBySettingKey(any())).thenReturn(Optional.empty());

        assertThat(service.claimTimeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(service.maxFileSizeBytes()).isEqualTo(2L * 1024 * 1024);
        assertThat(service.maxPlayersCap()).isEqualTo(12);
        assertThat(service.profileVisibilityWindowDays()).isEqualTo(14);
    }

    /** An override is what the platform uses, in the unit the caller asked for. */
    @Test
    void anOverrideReplacesTheDefaultInTheCallersUnit() {
        overridden(SettingKey.FILES_MAX_FILE_SIZE_MB, "5");
        overridden(SettingKey.ADMIN_QUEUE_CLAIM_TIMEOUT_MINUTES, "30");

        assertThat(service.maxFileSizeBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(service.claimTimeout()).isEqualTo(Duration.ofMinutes(30));
    }

    /**
     * A row somebody edited by hand into nonsense falls back to the default instead of throwing.
     *
     * <p>These accessors are on the hot path - every upload, every profile read - so the alternative
     * is one bad row taking those endpoints down. The default is always a working value, and the
     * listing is where the mismatch becomes visible.
     */
    @Test
    void aValueThatIsNotANumberFallsBackToTheDefault() {
        overridden(SettingKey.TABLES_MAX_PLAYERS_CAP, "doce");

        assertThat(service.maxPlayersCap()).isEqualTo(12);
    }

    /** The limits the client mirrors are the limits the upload endpoint will enforce, by construction. */
    @Test
    void theClientLimitsCarryTheSameCapTheUploadWillApply() {
        overridden(SettingKey.FILES_MAX_FILE_SIZE_MB, "7");
        when(settingRepository.findBySettingKey(SettingKey.ADMIN_QUEUE_CLAIM_TIMEOUT_MINUTES.wireName()))
                .thenReturn(Optional.empty());

        assertThat(service.clientLimits().maxFileSizeBytes()).isEqualTo(service.maxFileSizeBytes());
        assertThat(service.clientLimits().maxFileSizeBytes()).isEqualTo(7L * 1024 * 1024);
        assertThat(service.clientLimits().claimTimeoutMinutes()).isEqualTo(15);
    }

    // ---------------------------------------------------------------- the listing

    /** Every key appears, overridden or not, so the screen never has a blank row. */
    @Test
    void theListingCarriesEveryKeyWithItsDefaultBesideItsValue() {
        when(settingRepository.findAllWithActor()).thenReturn(List.of(override(SettingKey.TABLES_MAX_PLAYERS_CAP, "20")));

        List<SystemSettingResponse> rows = service.list();

        assertThat(rows).hasSize(SettingKey.values().length);
        SystemSettingResponse cap = row(rows, SettingKey.TABLES_MAX_PLAYERS_CAP);
        assertThat(cap.value()).isEqualTo(20);
        assertThat(cap.defaultValue()).isEqualTo(12);
        assertThat(cap.overridden()).isTrue();
        assertThat(cap.updatedByName()).isEqualTo("the-admin");

        SystemSettingResponse untouched = row(rows, SettingKey.FILES_MAX_FILE_SIZE_MB);
        assertThat(untouched.value()).isEqualTo(untouched.defaultValue());
        assertThat(untouched.overridden()).isFalse();
        assertThat(untouched.updatedByName()).isNull();
        assertThat(untouched.updatedAt()).isNull();
    }

    /**
     * {@code overridden} is not "the value differs from the default".
     *
     * <p>Setting something to exactly its default is a decision somebody made and signed, and the
     * screen has to be able to say so - a derived flag would erase it.
     */
    @Test
    void settingAKeyToItsOwnDefaultStillCountsAsOverridden() {
        when(settingRepository.findAllWithActor()).thenReturn(List.of(override(SettingKey.TABLES_MAX_PLAYERS_CAP, "12")));

        SystemSettingResponse cap = row(service.list(), SettingKey.TABLES_MAX_PLAYERS_CAP);
        assertThat(cap.value()).isEqualTo(cap.defaultValue());
        assertThat(cap.overridden()).isTrue();
    }

    // ---------------------------------------------------------------- writing

    /** The happy path, with the three things that have to happen together. */
    @Test
    void anUpdateWritesTheOverrideItsAuditRowAndDropsTheCache() {
        when(settingRepository.findBySettingKey(SettingKey.TABLES_MAX_PLAYERS_CAP.wireName())).thenReturn(Optional.empty());

        SystemSettingResponse after = service.update(SettingKey.TABLES_MAX_PLAYERS_CAP, 20, "una mesa grande", actor);

        assertThat(after.value()).isEqualTo(20);
        assertThat(after.overridden()).isTrue();

        ArgumentCaptor<SystemSettingChange> change = ArgumentCaptor.forClass(SystemSettingChange.class);
        verify(changeRepository).save(change.capture());
        assertThat(change.getValue().getSettingKey()).isEqualTo("tables.max_players_cap");
        assertThat(change.getValue().getToValue()).isEqualTo("20");
        assertThat(change.getValue().getJustification()).isEqualTo("una mesa grande");

        verify(cache).clear();
    }

    /**
     * The first change records {@code fromValue = null}, and that null is information: it says the
     * platform was still on the shipped default, which is a different fact from "it was already this
     * number".
     */
    @Test
    void theFirstChangeRecordsThatThereWasNoOverrideBefore() {
        when(settingRepository.findBySettingKey(any())).thenReturn(Optional.empty());

        service.update(SettingKey.ADMIN_QUEUE_CLAIM_TIMEOUT_MINUTES, 30, "las revisiones tardan", actor);

        ArgumentCaptor<SystemSettingChange> change = ArgumentCaptor.forClass(SystemSettingChange.class);
        verify(changeRepository).save(change.capture());
        assertThat(change.getValue().getFromValue()).isNull();
    }

    /** A later change records where it came from, which is what makes "raised and put back" readable. */
    @Test
    void aLaterChangeRecordsWhatItReplaced() {
        overridden(SettingKey.ADMIN_QUEUE_CLAIM_TIMEOUT_MINUTES, "30");

        service.update(SettingKey.ADMIN_QUEUE_CLAIM_TIMEOUT_MINUTES, 15, "volvemos atrás", actor);

        ArgumentCaptor<SystemSettingChange> change = ArgumentCaptor.forClass(SystemSettingChange.class);
        verify(changeRepository).save(change.capture());
        assertThat(change.getValue().getFromValue()).isEqualTo("30");
        assertThat(change.getValue().getToValue()).isEqualTo("15");
    }

    // ---------------------------------------------------------------- what it refuses

    /** Above the ceiling: a 400 carrying both bounds, because "out of range" alone says nothing (#197). */
    @Test
    void aValueAboveTheCeilingIsRefusedWithBothBoundsInTheError() {
        assertThatThrownBy(() -> service.update(SettingKey.FILES_MAX_FILE_SIZE_MB, 500, "más grande", actor))
                .isInstanceOfSatisfying(InvalidRequestException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("SETTING_OUT_OF_RANGE");
                    assertThat(error.getErrorParams()).containsEntry("minValue", "1").containsEntry("maxValue", "25");
                });
    }

    /** Below the floor, for the same reason: a visibility window of zero days hides everybody at once. */
    @Test
    void aValueBelowTheFloorIsRefused() {
        assertThatThrownBy(() -> service.update(SettingKey.PROFILES_VISIBILITY_WINDOW_DAYS, 0, "cero", actor))
                .isInstanceOf(InvalidRequestException.class);
    }

    /**
     * <b>Nothing is written when the value is refused</b>, and the assertion is that it was never
     * attempted rather than that nothing is stored - the same shape F3.2 used for #78's validation.
     */
    @Test
    void arefusedValueNeverReachesEitherTable() {
        assertThatThrownBy(() -> service.update(SettingKey.FILES_MAX_FILE_SIZE_MB, 0, "cero", actor))
                .isInstanceOf(ApiException.class);

        verify(settingRepository, never()).save(any());
        verify(changeRepository, never()).save(any());
        verify(cache, never()).clear();
    }

    // ---------------------------------------------------------------- the startup guard

    /**
     * <b>The file cap can never be raised past what the container accepts.</b>
     *
     * <p>{@code spring.servlet.multipart.max-file-size} rejects an oversized part before a byte
     * reaches a controller, so a setting allowed past it would produce a raw container failure where
     * the frontend expects the explained {@code FILE_TOO_LARGE} (#197). A comment asking the two
     * numbers to agree is not a mechanism; a boot failure naming both is.
     */
    @Test
    void itRefusesToStartWhenTheContainerCapIsNotAboveTheSettableCeiling() {
        SettingsService misconfigured = new SettingsService(
                settingRepository, changeRepository, userService, cacheManager, DataSize.ofMegabytes(25));

        assertThatThrownBy(misconfigured::requireMultipartHeadroom)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("files.max_file_size_mb");
    }

    /** And it starts when there is headroom, which is what {@code application.yml} configures. */
    @Test
    void itStartsWhenTheContainerCapIsAboveTheSettableCeiling() {
        service.requireMultipartHeadroom();
    }

    // ---------------------------------------------------------------- fixtures

    private void overridden(SettingKey key, String value) {
        when(settingRepository.findBySettingKey(key.wireName())).thenReturn(Optional.of(override(key, value)));
    }

    private SystemSetting override(SettingKey key, String value) {
        return new SystemSetting(key, value, actorUser);
    }

    private static SystemSettingResponse row(List<SystemSettingResponse> rows, SettingKey key) {
        return rows.stream().filter(row -> row.key() == key).findFirst().orElseThrow();
    }
}
