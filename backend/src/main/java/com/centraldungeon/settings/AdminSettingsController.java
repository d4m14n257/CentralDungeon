package com.centraldungeon.settings;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.settings.dto.SystemSettingChangeResponse;
import com.centraldungeon.settings.dto.SystemSettingResponse;
import com.centraldungeon.settings.dto.UpdateSettingRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /admin/settings}: the values the community changes without a deploy (#141).
 *
 * <p>Admin and Owner are enumerated on every method, as they are on every other administrative
 * endpoint: there is no {@code RoleHierarchy} in this project and an owner can do what an admin can
 * by being listed, not by inheriting (#37, #89, #123). <b>Editing settings is where the two roles
 * are the same</b> - the matrix of fase-3-admin-owner.md §3 says so in one line, and the only
 * difference F3 draws between them is who may grant the rank.
 *
 * <p>The actor always comes from {@code @AuthenticationPrincipal} and never from the body
 * (arquitectura.md §2.6): the audit row this writes is only worth anything if the name on it is not
 * one the caller chose.
 *
 * <p>No listing is paginated here. There are four settings and adding one is a line of an enum;
 * a page of four rows would be ceremony around a list nobody scrolls.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {

    /** Reading the catalogue, changing a value, and the trail each change leaves. */
    private final SettingsService settingsService;

    /**
     * @param settingsService the service that owns the values and their audit
     */
    public AdminSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Every setting the platform has, with what it is worth now and what it ships as.
     *
     * @return 200 with every key of {@link SettingKey}, grouped by category in the order the screen
     *         reads them. A key nobody ever changed appears too, showing its default
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public List<SystemSettingResponse> list() {
        return settingsService.list();
    }

    /**
     * Changes one setting.
     *
     * <p>A {@code PUT} and not a {@code POST} with an action verb, unlike the rest of F3: this really
     * is an assignment - the same call with the same value twice leaves the platform in the same
     * state - where {@code grant-role} and {@code block} are acts that happen once. The reason
     * travels in the body all the same, because a change nobody explained is a change nobody can
     * review (#141).
     *
     * @param key         which setting to change, as {@link SettingKey#wireName()} spells it
     * @param request     the new value and why
     * @param currentUser the actor, from the token (#121)
     * @return 200 with the setting afterwards. 400 when the value is outside the key's range, 404
     *         when no setting has that name
     */
    @PutMapping("/{key}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public SystemSettingResponse update(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return settingsService.update(SettingKey.from(key), request.value(), request.justification(), currentUser);
    }

    /**
     * What was done to one setting, oldest first.
     *
     * @param key which setting, as {@link SettingKey#wireName()} spells it
     * @return 200 with its whole history. Not paginated: it is read as a sequence inside the panel
     *         that opens on one setting. 404 when no setting has that name - an empty history and a
     *         key that does not exist are different answers
     */
    @GetMapping("/{key}/history")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public List<SystemSettingChangeResponse> history(@PathVariable String key) {
        return settingsService.history(SettingKey.from(key));
    }
}
