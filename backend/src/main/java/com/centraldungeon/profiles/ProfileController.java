package com.centraldungeon.profiles;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.profiles.dto.ProfileResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public profiles: the caller's own, and everybody else's under the five rules of modelo-datos.md §5
 * ("Visibilidad de perfiles": #41, #44, #45, #47).
 */
@RestController
@RequestMapping("/api/v1/users")
public class ProfileController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final ProfileService profileService;

    /**
     * @param profileService builds a profile once its visibility has been decided
     */
    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * The caller's own profile.
     *
     * @param currentUser the actor, from the token. There is no id parameter, so this can only ever
     *                    answer about the caller (#121)
     * @return 200 with their profile
     */
    @GetMapping("/me/profile")
    @PreAuthorize("isAuthenticated()")
    public ProfileResponse myProfile(@AuthenticationPrincipal CurrentUser currentUser) {
        return profileService.getProfile(currentUser.userId(), currentUser.userId());
    }

    /**
     * Somebody else's profile.
     *
     * <p>{@code id} names the person the profile describes, never the actor - the actor still only
     * ever comes from the token (#121). Whether it may be read is the five rules of §5.
     *
     * @param id          whose profile this is
     * @param currentUser the actor, from the token
     * @return 200 with the profile
     * @throws com.centraldungeon.common.exception.NotFoundException 404 if the person does not exist,
     *         or the actor may not see them - the two never distinguish themselves in the response
     *         (#249)
     */
    @GetMapping("/{id}/profile")
    @PreAuthorize("isAuthenticated()")
    public ProfileResponse profileOf(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return profileService.getProfile(id, currentUser.userId());
    }
}
