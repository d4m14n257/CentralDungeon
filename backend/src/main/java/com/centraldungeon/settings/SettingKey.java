package com.centraldungeon.settings;

import com.centraldungeon.common.exception.NotFoundException;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * Every setting the platform has, with its default and the range it may be moved inside (#141).
 *
 * <p><b>This enum is the catalogue, and {@code system_settings} holds overrides only.</b> A row
 * exists for a key after somebody changed it and never before, which is what lets the screen show
 * "current" beside "default" without seeding anything: the default is here, in code, and it is the
 * value the platform boots with on a database nobody has touched. It also means a setting can never
 * be missing - a fresh install and a wiped table behave identically.
 *
 * <p><b>Every key here has a consumer today.</b> #141 lists eight values worth moving out of the
 * code; F3.5 moves the four that something actually reads. Initial karma and the decay window (#97)
 * and the feedback window (#94) belong to subsystems F5 builds, and the default rejection reason of
 * #34 stopped being a sentence the backend owns when #197 turned it into the code
 * {@code TABLE_FULL} - see {@link SettingCategory} for why no {@code Texts} setting exists. A key
 * nothing reads is a switch that does nothing, which is the orphan F3.2 refused to create (#255).
 *
 * <p><b>The bounds are part of the setting, not of the form.</b> An admin who can type any number
 * into a box can take the platform down without writing a line of code: a visibility window of zero
 * days hides every profile at once, and a file cap above what the servlet container accepts makes
 * every upload fail with an error the application never gets to explain (#197). The range is
 * checked in {@link SettingsService}, so it holds for the API and not only for the screen.
 */
public enum SettingKey {

    /**
     * How long an admin keeps an item of the shared tray before the release job hands it back (#100).
     *
     * <p>Moved here from {@code app.admin-queue.claim-timeout}, which modelo-datos.md §5 named F3.5's
     * job by name. The starting value is the fifteen minutes #100 asks for; the floor is one minute
     * because a timeout shorter than the round trip of resolving something would hand every item back
     * mid-review, and the ceiling is a day because a reservation nobody can outlive is the deadlock
     * the release job exists to prevent.
     */
    ADMIN_QUEUE_CLAIM_TIMEOUT_MINUTES("admin_queue.claim_timeout_minutes", SettingCategory.Limits, 15, 1, 1440, false),

    /**
     * The per-file cap, in whole megabytes - the one piece of #61 that #75 kept.
     *
     * <p>Moved here from {@code app.storage.max-file-size}. <b>Its ceiling is not a taste
     * judgement</b>: {@code spring.servlet.multipart.max-file-size} rejects an oversized part before
     * a byte reaches a controller, so a setting above that limit would produce a failure the
     * application cannot explain in the reader's language. {@link SettingsService} refuses to start
     * if the two ever disagree.
     */
    FILES_MAX_FILE_SIZE_MB("files.max_file_size_mb", SettingCategory.Limits, 2, 1, 25, false),

    /**
     * The largest {@code max_players} a table may be given (#24, #141).
     *
     * <p><b>New in F3.5, and the one key that was not a constant before</b>: until now
     * {@code maxPlayers} was only {@code @Positive}, so a table could be created for two billion
     * people. #141 names «cupo máximo» among the values the community edits without a deploy, and a
     * limit that does not exist cannot be edited.
     *
     * <p>It gates what is written, never what is already stored: lowering it does not shrink an
     * existing table, and the next edit of a table already above the cap is what will be refused.
     */
    TABLES_MAX_PLAYERS_CAP("tables.max_players_cap", SettingCategory.Limits, 12, 1, 100, false),

    /**
     * How long a closed table keeps two of its people able to see each other's profile, in days
     * (#44).
     *
     * <p>Moved here from a constant in {@code ProfileVisibilityService}. <b>The one retroactive
     * setting F3.5 ships</b>: #44 is evaluated on every profile read against {@code closed_at}, so
     * lowering this number takes visibility away from people who have it right now, and raising it
     * gives it back to people who had already lost it. Nothing is recomputed and nothing is stored -
     * the answer simply changes on the next read, which is why {@link #retroactive()} exists and why
     * the screen has to say so before the value is saved.
     */
    PROFILES_VISIBILITY_WINDOW_DAYS("profiles.visibility_window_days", SettingCategory.Business, 14, 1, 3650, true);

    /** How the key is spelled everywhere outside this enum: the column, the URL, the JSON, the i18n key. */
    private final String wireName;

    /** Which group the screen files it under. */
    private final SettingCategory category;

    /** What the platform uses when nothing has overridden the key. */
    private final int defaultValue;

    /** The smallest value that may be saved. */
    private final int minValue;

    /** The largest value that may be saved. */
    private final int maxValue;

    /** Whether changing it changes answers that were already given. */
    private final boolean retroactive;

    /**
     * @param wireName     the spelling used in the column, the URL and the JSON
     * @param category     which group {@code /admin/settings} files it under
     * @param defaultValue what the platform uses until somebody overrides it
     * @param minValue     the smallest value that may be saved, inclusive
     * @param maxValue     the largest value that may be saved, inclusive
     * @param retroactive  whether changing it changes answers the platform already gave (#141)
     */
    SettingKey(String wireName, SettingCategory category, int defaultValue, int minValue, int maxValue, boolean retroactive) {
        this.wireName = wireName;
        this.category = category;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.retroactive = retroactive;
    }

    /**
     * Returns the spelling the key has everywhere outside this enum.
     *
     * <p>{@code @JsonValue} for the same reason {@code PlatformRole.roleName()} carries it (#253):
     * the constants are {@code SCREAMING_CASE} and the wire says {@code files.max_file_size_mb},
     * so without it a response would publish a name no request could send back - and the screen
     * builds its labels from this string, so the mismatch would surface as a raw i18n key rather
     * than as an error anybody could read.
     *
     * @return the key as the column, the URL and the JSON spell it
     */
    @JsonValue
    public String wireName() {
        return wireName;
    }

    /**
     * Returns which group {@code /admin/settings} files this setting under.
     *
     * @return the category, never null
     */
    public SettingCategory category() {
        return category;
    }

    /**
     * Returns how the stored text is read back.
     *
     * <p>Not a per-constant field: every setting F3.5 ships is a whole number, and a field whose
     * every value is the same is noise that suggests variation which does not exist. It becomes a
     * constructor parameter the day a second type arrives, which is the point at which it starts
     * carrying information ({@link SettingValueType}).
     *
     * @return the value type, which is {@link SettingValueType#Integer} for every key today
     */
    public SettingValueType valueType() {
        return SettingValueType.Integer;
    }

    /**
     * Returns what the platform uses when nothing has overridden this key.
     *
     * @return the shipped default
     */
    public int defaultValue() {
        return defaultValue;
    }

    /**
     * Returns the smallest value that may be saved.
     *
     * @return the inclusive lower bound
     */
    public int minValue() {
        return minValue;
    }

    /**
     * Returns the largest value that may be saved.
     *
     * @return the inclusive upper bound
     */
    public int maxValue() {
        return maxValue;
    }

    /**
     * Returns whether changing this setting changes answers the platform already gave (#141).
     *
     * <p>It travels to the frontend as a flag and not as a sentence (#197): the warning is written
     * once per key in {@code src/locales/}, in both languages, and the flag is what makes the screen
     * show it. A setting marked this way is never a cosmetic adjustment and the screen has to say so
     * <em>before</em> the value is saved, not after.
     *
     * @return true when the new value re-answers questions that were already answered
     */
    public boolean retroactive() {
        return retroactive;
    }

    /**
     * Looks a key up by the name the API publishes.
     *
     * <p>Exact, never case-insensitive, and that is the difference with
     * {@code PlatformRole.fromRoleName}: this name never arrives from a search box somebody is
     * typing into. It arrives as a path segment the frontend copied from a response, so anything
     * else is a caller inventing a setting.
     *
     * @param wireName the key as a response spelled it
     * @return the matching constant
     * @throws NotFoundException 404 when no setting has that name. A 404 and not a 400: the request
     *                           is well formed and names a resource that does not exist, which is
     *                           the same answer any other unknown id gets
     */
    public static SettingKey from(String wireName) {
        return Arrays.stream(values())
                .filter(key -> key.wireName.equals(wireName))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Setting not found: " + wireName));
    }

    /**
     * Every setting, grouped the way {@code /admin/settings} reads them: by category, and inside a
     * category in declaration order.
     *
     * <p>Declaration order and not alphabetical, because the order is editorial - it is the order
     * somebody reading the screen for the first time should meet them in - and sorting by name would
     * hand that decision to how the keys happen to be spelled.
     *
     * @return the keys, ordered
     */
    public static List<SettingKey> ordered() {
        return Arrays.stream(values())
                .sorted((left, right) -> {
                    int byCategory = left.category.compareTo(right.category);
                    return byCategory != 0 ? byCategory : Integer.compare(left.ordinal(), right.ordinal());
                })
                .toList();
    }
}
