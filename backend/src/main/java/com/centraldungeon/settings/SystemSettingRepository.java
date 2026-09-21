package com.centraldungeon.settings;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The {@code system_settings} table - the overrides, never the catalogue. What settings exist is
 * {@link SettingKey}.
 */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    /**
     * Reads one override by its key.
     *
     * @param settingKey the key, as {@link SettingKey#wireName()} spells it
     * @return the override, or empty when the platform is still on the shipped default
     */
    Optional<SystemSetting> findBySettingKey(String settingKey);

    /**
     * Every override there is, with the person who made it already loaded.
     *
     * <p>The join is the point: the listing prints who has each setting like this, and twenty lazy
     * {@code updatedBy} proxies would be twenty queries for a screen that shows one table.
     *
     * @return every row of {@code system_settings}, its actor fetched
     */
    @Query("select s from SystemSetting s left join fetch s.updatedBy")
    List<SystemSetting> findAllWithActor();
}
