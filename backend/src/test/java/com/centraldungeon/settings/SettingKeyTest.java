package com.centraldungeon.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.common.exception.NotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The catalogue's own invariants - the ones a typo in a single enum constant would break silently.
 *
 * <p>Every one of these is about a mistake that produces no error anywhere else: a duplicated wire
 * name makes {@link SettingKey#from} resolve to whichever constant comes first, and a default outside
 * its own bounds makes a setting that the API refuses to save back the value it is already using.
 */
class SettingKeyTest {

    /** A key resolves by the exact name the API publishes, and by nothing else. */
    @Test
    void resolvesByTheNameTheApiPublishes() {
        assertThat(SettingKey.from("files.max_file_size_mb")).isEqualTo(SettingKey.FILES_MAX_FILE_SIZE_MB);
    }

    /**
     * An unknown name is a 404 and not a 400: the request is well formed and names a setting that
     * does not exist, which is the same answer every other unknown id gets.
     */
    @Test
    void anUnknownNameIsNotFound() {
        assertThatThrownBy(() -> SettingKey.from("files.max_file_size"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("files.max_file_size");
    }

    /**
     * Exact, never case-insensitive - unlike {@code PlatformRole.fromRoleName}, because this name
     * never arrives from a box somebody is typing into.
     */
    @Test
    void theLookupIsCaseSensitive() {
        assertThatThrownBy(() -> SettingKey.from("FILES.MAX_FILE_SIZE_MB")).isInstanceOf(NotFoundException.class);
    }

    /** Two settings sharing a wire name would make one of them unreachable, and nothing else would say so. */
    @Test
    void everyWireNameIsUnique() {
        Set<String> names = new HashSet<>();
        for (SettingKey key : SettingKey.values()) {
            assertThat(names.add(key.wireName())).as("duplicate wire name %s", key.wireName()).isTrue();
        }
    }

    /**
     * The default has to be a value the API would accept. Otherwise a setting ships working and
     * cannot be put back: an admin who changes it is refused when they type the original number in.
     */
    @ParameterizedTest
    @EnumSource(SettingKey.class)
    void everyDefaultIsInsideItsOwnRange(SettingKey key) {
        assertThat(key.defaultValue()).isBetween(key.minValue(), key.maxValue());
    }

    /** A range whose floor is above its ceiling accepts nothing at all. */
    @ParameterizedTest
    @EnumSource(SettingKey.class)
    void everyRangeHasRoomInIt(SettingKey key) {
        assertThat(key.minValue()).isLessThanOrEqualTo(key.maxValue());
    }

    /**
     * The order the screen reads them in: grouped by category, and inside a category as declared.
     * Declaration order is editorial, so sorting by name would hand that decision to the spelling.
     */
    @Test
    void orderedGroupsByCategoryAndKeepsDeclarationOrderInside() {
        List<SettingKey> ordered = SettingKey.ordered();

        assertThat(ordered).containsExactlyInAnyOrderElementsOf(Arrays.asList(SettingKey.values()));
        assertThat(ordered.stream().map(SettingKey::category))
                .containsExactly(
                        SettingCategory.Business,
                        SettingCategory.Limits,
                        SettingCategory.Limits,
                        SettingCategory.Limits);
        assertThat(ordered.get(0)).isEqualTo(SettingKey.PROFILES_VISIBILITY_WINDOW_DAYS);
    }

    /**
     * <b>The one retroactive setting F3.5 ships</b> (#44). The flag is what makes the screen warn
     * before saving, and #141 asks for that warning by name - so a key quietly losing it would turn a
     * change that takes visibility away from people right now into an adjustment nobody was told
     * about.
     */
    @Test
    void onlyTheVisibilityWindowIsRetroactive() {
        assertThat(Arrays.stream(SettingKey.values()).filter(SettingKey::retroactive).toList())
                .containsExactly(SettingKey.PROFILES_VISIBILITY_WINDOW_DAYS);
    }

    /** Every key ships as an Integer today, which is what {@link SettingValueType} documents. */
    @ParameterizedTest
    @EnumSource(SettingKey.class)
    void everyKeyIsAnInteger(SettingKey key) {
        assertThat(key.valueType()).isEqualTo(SettingValueType.Integer);
    }
}
