package com.centraldungeon.settings;

import com.centraldungeon.settings.dto.ClientLimitsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /settings}: the part of the configuration the interface has to know in order to state a
 * limit before somebody breaks it (principio 2 de frontend-diseno.md §1).
 *
 * <p><b>A second controller and not a method on {@link AdminSettingsController}</b>, for the same
 * reason {@code UserController} and {@code AdminUserController} are two: the audience is different.
 * Everything under {@code /admin/settings} is «configuración», answers only to Admin and Owner, and
 * shows who set what; this answers to anybody with a session and carries nothing that is a decision
 * about another person.
 *
 * <p>{@code isAuthenticated()} and no role, because the caller is whoever is about to upload a file,
 * which is everybody. It is not a public endpoint either - an unauthenticated reader has no upload
 * to be warned about, so there is nothing here for them.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    /** The values, read through the typed accessors so this can never publish a different limit. */
    private final SettingsService settingsService;

    /**
     * @param settingsService the service that owns the values
     */
    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * The limits the interface mirrors.
     *
     * @return 200 with the caps a client checks against before sending anything. Never the authority:
     *         the server applies the same limit again and answers with the real number (#197)
     */
    @GetMapping("/limits")
    @PreAuthorize("isAuthenticated()")
    public ClientLimitsResponse limits() {
        return settingsService.clientLimits();
    }
}
