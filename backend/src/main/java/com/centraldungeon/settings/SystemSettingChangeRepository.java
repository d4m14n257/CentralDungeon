package com.centraldungeon.settings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The audit trail of {@code /admin/settings} (#141). Append-only: nothing here is ever updated or
 * deleted, so the repository has no method that could.
 */
public interface SystemSettingChangeRepository extends JpaRepository<SystemSettingChange, String> {

    /**
     * What was done to one setting, oldest first - the same order every other history in the
     * application is read in.
     *
     * <p>The join loads the actor with the row: the panel prints who made each change, and reading
     * ten changes would otherwise be ten extra queries.
     *
     * @param settingKey the setting, as {@link SettingKey#wireName()} spells it
     * @return its changes, oldest first
     */
    @Query("select c from SystemSettingChange c left join fetch c.changedBy where c.settingKey = :settingKey order by c.createdAt asc")
    List<SystemSettingChange> findBySettingKeyOrderByCreatedAtAsc(String settingKey);
}
