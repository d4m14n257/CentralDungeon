package com.centraldungeon.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.settings.SettingKey;
import com.centraldungeon.settings.SettingsService;
import com.centraldungeon.users.AdminUserService;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserRoleService;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The two Caffeine caches of the application observed as caches: that a read fills them, that a
 * change made behind the application's back is not seen until the entry goes, that the real path of a
 * change drops the entry, and that it does so only once the change is committed.
 *
 * <p>The HTTP suites only ever see the effect of an eviction (a blocked person gets refused); a
 * cache that never filled would pass them too. Here every step reads the {@link CacheManager}
 * itself, and the "behind its back" step is plain SQL, which no service sees.
 *
 * <p>Both caches are emptied before each test because the database is emptied with SQL as well, and
 * the cache of a previous test would otherwise answer for rows that no longer exist.
 */
class AuthCacheIT extends AbstractRoleMatrixIT {

    /** Cache of the status and roles a request is authenticated with. */
    private static final String USER_AUTH = "userAuth";

    /** Cache of the editable platform limits. */
    private static final String SYSTEM_SETTINGS = "systemSettings";

    /** The key {@link SettingsService#profileVisibilityWindowDays()} caches under. */
    private static final String WINDOW_KEY = "profiles.visibility_window_days";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private UserService userService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private SettingsService settingsService;

    @Override
    List<MatrixRow> rows() {
        return List.of();
    }

    @BeforeEach
    void emptyCaches() {
        cache(USER_AUTH).clear();
        cache(SYSTEM_SETTINGS).clear();
    }

    /** The policy of the cache is the revocation window the session design promises. */
    @Test
    void userAuthExpiresAfterSixtySecondsAndIsBounded() {
        CaffeineCache caffeine = (CaffeineCache) cache(USER_AUTH);

        assertThat(caffeine.getNativeCache().policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.SECONDS))
                .isEqualTo(60);
        assertThat(caffeine.getNativeCache().policy().eviction().orElseThrow().getMaximum()).isEqualTo(10_000);
    }

    /** The settings cache shares that policy: SQL changed behind the application is bounded by it. */
    @Test
    void systemSettingsExpiresAfterSixtySeconds() {
        CaffeineCache caffeine = (CaffeineCache) cache(SYSTEM_SETTINGS);

        assertThat(caffeine.getNativeCache().policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.SECONDS))
                .isEqualTo(60);
    }

    /** Blocking an account drops its entry, and the next read is the database's current truth. */
    @Test
    void blockingAnAccountEvictsItsAuthSnapshot() {
        MatrixFixtures world = MatrixFixtures.forCell(context, MatrixActor.ANONYMOUS);
        User target = world.person("target", PlatformRole.PLAYER);
        CurrentUser admin = admin(world);

        UserAuthSnapshot loaded = userService.loadAuthSnapshot(target.getId());
        assertThat(loaded.status()).isEqualTo(UserStatus.Allowed);
        assertThat(loaded.roles()).containsExactly("Player");
        assertThat(cache(USER_AUTH).get(target.getId())).isNotNull();

        // Behind the application's back: a role the cached snapshot does not know about.
        grantBySql(target, "Master");
        assertThat(userService.loadAuthSnapshot(target.getId()).roles()).containsExactly("Player");

        adminUserService.block(target.getId(), "Cache test", admin);

        assertThat(cache(USER_AUTH).get(target.getId())).isNull();
        UserAuthSnapshot fresh = userService.loadAuthSnapshot(target.getId());
        assertThat(fresh.status()).isEqualTo(UserStatus.Blocked);
        assertThat(fresh.roles()).containsExactlyInAnyOrder("Player", "Master");
    }

    /** Changing a role drops the entry too, and the next read sees the status that moved in the meantime. */
    @Test
    void changingARoleEvictsTheAuthSnapshot() {
        MatrixFixtures world = MatrixFixtures.forCell(context, MatrixActor.ANONYMOUS);
        User target = world.person("target", PlatformRole.PLAYER);
        CurrentUser admin = admin(world);

        assertThat(userService.loadAuthSnapshot(target.getId()).status()).isEqualTo(UserStatus.Allowed);
        assertThat(cache(USER_AUTH).get(target.getId())).isNotNull();

        // Behind the application's back: the status, which the cached snapshot does not know about.
        jdbc.update("UPDATE users SET status = 'Blocked' WHERE id = ?", target.getId());
        assertThat(userService.loadAuthSnapshot(target.getId()).status()).isEqualTo(UserStatus.Allowed);

        userRoleService.grantRole(target.getId(), PlatformRole.MASTER, "Cache test", admin);

        assertThat(cache(USER_AUTH).get(target.getId())).isNull();
        UserAuthSnapshot fresh = userService.loadAuthSnapshot(target.getId());
        assertThat(fresh.status()).isEqualTo(UserStatus.Blocked);
        assertThat(fresh.roles()).containsExactlyInAnyOrder("Player", "Master");
    }

    /**
     * The entry survives while the changing transaction is still open and goes the moment it
     * commits: evicting earlier would let a concurrent request refill it with the old answer.
     */
    @Test
    void theAuthSnapshotIsEvictedOnlyAfterTheCommit() {
        MatrixFixtures world = MatrixFixtures.forCell(context, MatrixActor.ANONYMOUS);
        User target = world.person("target", PlatformRole.PLAYER);
        CurrentUser admin = admin(world);
        userService.loadAuthSnapshot(target.getId());

        transactions.executeWithoutResult(status -> {
            adminUserService.block(target.getId(), "Cache test", admin);
            assertThat(cache(USER_AUTH).get(target.getId())).as("still cached inside the transaction").isNotNull();
        });

        assertThat(cache(USER_AUTH).get(target.getId())).as("evicted after the commit").isNull();
    }

    /** A setting is cached on first read, stays stale when SQL changes it, and updates through the service. */
    @Test
    void changingASettingEvictsTheSettingsCache() {
        MatrixFixtures world = MatrixFixtures.forCell(context, MatrixActor.ANONYMOUS);
        CurrentUser admin = admin(world);
        int fallback = SettingKey.PROFILES_VISIBILITY_WINDOW_DAYS.defaultValue();

        assertThat(settingsService.profileVisibilityWindowDays()).isEqualTo(fallback);
        assertThat(cache(SYSTEM_SETTINGS).get(WINDOW_KEY)).isNotNull();

        // Behind the application's back: an override the cached value does not know about.
        jdbc.update(
                "INSERT INTO system_settings (setting_key, value, value_type, category, updated_by, updated_at) "
                        + "VALUES (?, '30', 'Integer', 'Business', ?, NOW())",
                WINDOW_KEY,
                admin.userId());
        assertThat(settingsService.profileVisibilityWindowDays()).isEqualTo(fallback);

        settingsService.update(SettingKey.PROFILES_VISIBILITY_WINDOW_DAYS, 45, "Cache test", admin);

        assertThat(cache(SYSTEM_SETTINGS).get(WINDOW_KEY)).isNull();
        assertThat(settingsService.profileVisibilityWindowDays()).isEqualTo(45);
        assertThat(cache(SYSTEM_SETTINGS).get(WINDOW_KEY)).isNotNull();
    }

    /** The settings cache is cleared only after the commit, for the same reason as the auth one. */
    @Test
    void theSettingsCacheIsClearedOnlyAfterTheCommit() {
        MatrixFixtures world = MatrixFixtures.forCell(context, MatrixActor.ANONYMOUS);
        CurrentUser admin = admin(world);
        settingsService.profileVisibilityWindowDays();

        transactions.executeWithoutResult(status -> {
            settingsService.update(SettingKey.PROFILES_VISIBILITY_WINDOW_DAYS, 45, "Cache test", admin);
            assertThat(cache(SYSTEM_SETTINGS).get(WINDOW_KEY)).as("still cached inside the transaction").isNotNull();
        });

        assertThat(cache(SYSTEM_SETTINGS).get(WINDOW_KEY)).as("cleared after the commit").isNull();
    }

    private Cache cache(String name) {
        Cache cache = cacheManager.getCache(name);
        assertThat(cache).as("cache " + name).isNotNull();
        return cache;
    }

    /** An administrator the changes are made by. */
    private static CurrentUser admin(MatrixFixtures world) {
        User admin = world.person("admin", PlatformRole.ADMIN);
        return new CurrentUser(admin.getId(), Set.of("Admin"));
    }

    /** Gives a role with plain SQL, the way no service does. */
    private void grantBySql(User user, String roleName) {
        jdbc.update(
                "INSERT INTO users_roles (user_id, role_id, status, created_at) "
                        + "SELECT ?, id, 'Allowed', NOW() FROM roles WHERE name = ?",
                user.getId(),
                roleName);
    }
}
