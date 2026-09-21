package com.centraldungeon.settings;

import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.settings.dto.ClientLimitsResponse;
import com.centraldungeon.settings.dto.SystemSettingChangeResponse;
import com.centraldungeon.settings.dto.SystemSettingResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

/**
 * The values the community changes without a deploy (#141), and the four accessors the rest of the
 * application reads them through.
 *
 * <h2>Typed accessors, never a lookup by string</h2>
 *
 * <p>Regla dura 3 is about what crosses HTTP, and #141 extends the same idea inwards: the storage is
 * generic - one table, any key - and <b>nothing outside this class ever sees that</b>. A caller asks
 * {@link #maxFileSizeBytes()} and gets a {@code long} in the unit it needs; it never learns that the
 * number is stored in megabytes, as text, in a row that may not exist. That is what makes adding a
 * setting cheap and reading one impossible to get wrong.
 *
 * <h2>Why each accessor carries its own {@code @Cacheable}</h2>
 *
 * <p>These are read on the hot path - every upload, every profile read, every minute by the release
 * job - and a settings table queried once per request is a query per request. The cache is the same
 * Caffeine instance #128 introduced, under its own name.
 *
 * <p>The annotation sits on each accessor rather than on one shared {@code readInt} underneath
 * because Spring's caching is a proxy: a call this class makes to itself does not pass through it,
 * so a cached private helper would be a cache that never fills. Four annotations and a private
 * helper they all share is the shape that actually caches.
 *
 * <p>None of the four carries {@code @Transactional} either. Each is a single read that Spring Data
 * already wraps, and stacking the two interceptors on one method would put the transaction's
 * boundary and the cache's in an order neither annotation states.
 *
 * <h2>Why the eviction waits for the commit</h2>
 *
 * <p>Same rule as {@code AdminUserService} and the same reason (#128): evicting inside the
 * transaction opens a window in which a concurrent request misses the cache, reads the row as it was
 * <em>before</em> the commit, and repopulates the entry with the value the change was meant to
 * replace - where it then survives the whole TTL. So the clear runs on {@code afterCommit}, and it
 * is done through the {@link CacheManager} rather than a {@code @CacheEvict} annotation for the
 * proxy reason above: the callback is this bean calling itself.
 *
 * <p>Per JVM, like every other cache here (#128, #101): with more than one instance running, a
 * change would take up to the TTL to reach the others. That is the day the shared cache of
 * plan-desarrollo.md §8 is needed, and not before.
 */
@Service
public class SettingsService {

    /** The name of the Caffeine cache these values live in, declared in {@code application.yml}. */
    private static final String CACHE_NAME = "systemSettings";

    /** One megabyte, for turning the stored unit into the bytes callers actually compare against. */
    private static final long BYTES_PER_MB = 1024L * 1024L;

    /** Rejecting a value outside the range its key allows. */
    private static final String SETTING_OUT_OF_RANGE = "SETTING_OUT_OF_RANGE";

    /** The overrides. Absent key means the platform is still on {@link SettingKey#defaultValue()}. */
    private final SystemSettingRepository settingRepository;

    /** The audit trail (#141), written on every change and read by the history panel. */
    private final SystemSettingChangeRepository changeRepository;

    /** Resolves the actor of a change into the {@code User} row the audit points at. */
    private final UserService userService;

    /** Clears the cached values once a change is committed. */
    private final CacheManager cacheManager;

    /**
     * The container's own multipart cap, checked against the file setting's ceiling at startup.
     *
     * <p><b>Read from the environment and not injected as {@code MultipartProperties}</b>, which is
     * what the first version did. That bean only exists in a servlet context, so every integration
     * test that boots the application without a web environment - seventeen of them, none about
     * settings - failed to start with a missing-bean error that reads like broken code. The value is
     * the same value; where it is taken from is what decides whether this class can be constructed at
     * all.
     */
    private final DataSize containerMultipartCap;

    /**
     * @param settingRepository     the overrides
     * @param changeRepository      the audit trail
     * @param userService           resolves the actor of a change
     * @param cacheManager          clears the cached values after a committed change
     * @param containerMultipartCap the container's multipart cap, for the startup check
     */
    public SettingsService(
            SystemSettingRepository settingRepository,
            SystemSettingChangeRepository changeRepository,
            UserService userService,
            CacheManager cacheManager,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize containerMultipartCap) {
        this.settingRepository = settingRepository;
        this.changeRepository = changeRepository;
        this.userService = userService;
        this.cacheManager = cacheManager;
        this.containerMultipartCap = containerMultipartCap;
    }

    /**
     * Refuses to start if the file cap could ever be raised above what the servlet container accepts.
     *
     * <p>{@code spring.servlet.multipart.max-file-size} rejects an oversized part <b>before</b> a
     * byte reaches a controller, so a setting allowed to go past it would let an admin configure a
     * limit that produces a raw container error instead of the explained {@code FILE_TOO_LARGE} the
     * frontend renders (#197). The two numbers have to be kept in agreement by somebody, and a
     * comment asking them to is not a mechanism - a boot failure naming both values is.
     *
     * <p>Strictly greater and not "at least": the container is the backstop, so the application's own
     * rule has to be the one that normally answers, which is what {@code application.yml} already
     * says in so many words.
     *
     * @throws IllegalStateException when the configured container cap is not strictly above the
     *                               largest value {@link SettingKey#FILES_MAX_FILE_SIZE_MB} allows
     */
    @PostConstruct
    void requireMultipartHeadroom() {
        long settableCeiling = SettingKey.FILES_MAX_FILE_SIZE_MB.maxValue() * BYTES_PER_MB;
        long containerCap = containerMultipartCap.toBytes();
        if (containerCap <= settableCeiling) {
            throw new IllegalStateException(
                    "spring.servlet.multipart.max-file-size is " + containerCap + " bytes, which is not above the "
                            + settableCeiling + " bytes " + SettingKey.FILES_MAX_FILE_SIZE_MB.wireName()
                            + " can be raised to. Raise the container cap or lower the setting's maxValue.");
        }
    }

    /**
     * The per-file upload cap, in bytes (#75, #141).
     *
     * @return the cap, converted from the whole megabytes the setting stores
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "'files.max_file_size_mb'")
    public long maxFileSizeBytes() {
        return readInt(SettingKey.FILES_MAX_FILE_SIZE_MB) * BYTES_PER_MB;
    }

    /**
     * The largest {@code max_players} a table may be given (#24, #141).
     *
     * @return the cap, in people
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "'tables.max_players_cap'")
    public int maxPlayersCap() {
        return readInt(SettingKey.TABLES_MAX_PLAYERS_CAP);
    }

    /**
     * How long an admin keeps an item of the shared tray before the release job hands it back (#100).
     *
     * @return the timeout, built from the whole minutes the setting stores
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "'admin_queue.claim_timeout_minutes'")
    public Duration claimTimeout() {
        return Duration.ofMinutes(readInt(SettingKey.ADMIN_QUEUE_CLAIM_TIMEOUT_MINUTES));
    }

    /**
     * How long a closed table keeps two of its people able to see each other's profile (#44).
     *
     * @return the window, in days
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "'profiles.visibility_window_days'")
    public int profileVisibilityWindowDays() {
        return readInt(SettingKey.PROFILES_VISIBILITY_WINDOW_DAYS);
    }

    /**
     * The limits the interface states before somebody breaks one.
     *
     * <p>Built from the accessors and not from the repository, so it can never disagree with what the
     * upload endpoint will actually enforce.
     *
     * <p>It calls the accessors from inside this bean, so the cache is bypassed and the table is
     * read. That is the right way round here: this answer is fetched once when a screen
     * opens and is what a client will refuse files against for as long as it stays open, so it is
     * worth a query and worth being the freshest number in the system.
     *
     * @return the caps the client mirrors
     */
    @Transactional(readOnly = true)
    public ClientLimitsResponse clientLimits() {
        return new ClientLimitsResponse(maxFileSizeBytes(), (int) claimTimeout().toMinutes());
    }

    /**
     * Every setting, in the order the screen reads them: by category, then as declared.
     *
     * <p><b>Reads the table directly and never the cache.</b> The accessors above answer "what is the
     * platform using", which is worth caching for sixty seconds; this one answers "what is
     * configured, by whom, and when", which an admin is looking at in order to change it. A listing
     * served from a cache would show somebody the value they just replaced.
     *
     * @return one row per key of {@link SettingKey}, override or default
     */
    @Transactional(readOnly = true)
    public List<SystemSettingResponse> list() {
        Map<String, SystemSetting> overrides = settingRepository.findAllWithActor().stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, Function.identity()));
        List<SystemSettingResponse> rows = new ArrayList<>();
        for (SettingKey key : SettingKey.ordered()) {
            rows.add(toResponse(key, overrides.get(key.wireName())));
        }
        return List.copyOf(rows);
    }

    /**
     * Changes one setting (#141).
     *
     * <p>Three things happen together or none of them do: the override is written, the change is
     * recorded with its reason, and the cached value is dropped once the transaction commits. The
     * audit row is not optional bookkeeping - a platform-wide change produces no notification and no
     * visible event, so without it the only trace is a number that used to be different.
     *
     * @param key           which setting to change
     * @param value         the new value
     * @param justification why, never blank
     * @param actor         the actor, from the token (#121)
     * @return the setting as it is after the change
     * @throws InvalidRequestException 400 {@code SETTING_OUT_OF_RANGE}, carrying the bounds, when the
     *                                 value is outside what the key allows. The message needs the
     *                                 numbers in it - "out of range" without them says nothing about
     *                                 what to type instead (#197)
     */
    @Transactional
    public SystemSettingResponse update(SettingKey key, int value, String justification, CurrentUser actor) {
        requireWithinRange(key, value);

        User changedBy = userService.getById(actor.userId());
        Optional<SystemSetting> existing = settingRepository.findBySettingKey(key.wireName());
        String fromValue = existing.map(SystemSetting::getValue).orElse(null);
        String toValue = String.valueOf(value);

        SystemSetting setting = existing.orElseGet(() -> new SystemSetting(key, toValue, changedBy));
        setting.update(toValue, changedBy);
        setting = settingRepository.save(setting);

        changeRepository.save(new SystemSettingChange(key, fromValue, toValue, changedBy, justification));
        evictAfterCommit();

        return toResponse(key, setting);
    }

    /**
     * What was done to one setting, oldest first.
     *
     * @param key the setting
     * @return its changes, each with the actor already resolved to a display name. Empty when nobody
     *         has ever changed it - which is a different answer from a key that does not exist, and
     *         {@link SettingKey#from} is what makes that one a 404
     */
    @Transactional(readOnly = true)
    public List<SystemSettingChangeResponse> history(SettingKey key) {
        return changeRepository.findBySettingKeyOrderByCreatedAtAsc(key.wireName()).stream()
                .map(change -> new SystemSettingChangeResponse(
                        change.getId(),
                        key,
                        change.getFromValue(),
                        change.getToValue(),
                        displayNameOf(change.getChangedBy()),
                        change.getJustification(),
                        change.getCreatedAt()))
                .toList();
    }

    /**
     * The effective value of a key: the override if there is one, the shipped default otherwise.
     *
     * <p>Private on purpose. Everything outside this class goes through a typed accessor, so nobody
     * else has to remember which unit a key is stored in - and nobody can read a setting that has no
     * accessor, which is what keeps an unused key from quietly becoming a used one.
     */
    private int readInt(SettingKey key) {
        return settingRepository.findBySettingKey(key.wireName())
                .map(setting -> parse(key, setting.getValue()))
                .orElseGet(key::defaultValue);
    }

    /**
     * Reads a stored value back, falling back to the default when the text is not a number.
     *
     * <p>It can only happen if somebody edited the table by hand, and the alternative to falling back
     * is throwing on the hot path: every upload and every profile read would start failing because of
     * one bad row. The default is always a working value, so the platform keeps running and the
     * screen shows the mismatch.
     */
    private static int parse(SettingKey key, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            return key.defaultValue();
        }
    }

    /** The bound belongs to the key, so it is checked where the key is known and not in the record. */
    private static void requireWithinRange(SettingKey key, int value) {
        if (value < key.minValue() || value > key.maxValue()) {
            throw new InvalidRequestException(
                    "Setting " + key.wireName() + " must be between " + key.minValue() + " and " + key.maxValue()
                            + ", got " + value,
                    SETTING_OUT_OF_RANGE,
                    Map.of(
                            "minValue", String.valueOf(key.minValue()),
                            "maxValue", String.valueOf(key.maxValue())));
        }
    }

    /** One row of the listing, from the key and the override there may or may not be. */
    private static SystemSettingResponse toResponse(SettingKey key, @Nullable SystemSetting override) {
        return new SystemSettingResponse(
                key,
                key.category(),
                key.valueType(),
                override == null ? key.defaultValue() : parse(key, override.getValue()),
                key.defaultValue(),
                key.minValue(),
                key.maxValue(),
                override != null,
                key.retroactive(),
                override == null ? null : displayNameOf(override.getUpdatedBy()),
                override == null ? null : override.getUpdatedAt());
    }

    /** The screen shows a person, not an id - the same fallback every other history uses. */
    private static String displayNameOf(User user) {
        return user.getName() != null ? user.getName() : user.getDiscordUsername();
    }

    /**
     * Drops every cached value - once the change is actually committed.
     *
     * <p>All entries and not the one key that moved: the cost is four misses on the next reads, and
     * the alternative is a per-key eviction that has to be kept in step with every accessor added
     * later. A cache that is cleared too much is slow for a second; one that is cleared too little is
     * wrong for a minute.
     *
     * <p>Called without a transaction - a unit test, or a future caller that forgets one - it clears
     * immediately, which is the best that can be done when there is no commit to wait for.
     */
    private void evictAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clearCache();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clearCache();
            }
        });
    }

    /** Empties the settings cache, if one is configured at all. */
    private void clearCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }
}
