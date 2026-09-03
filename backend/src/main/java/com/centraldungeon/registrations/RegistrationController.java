package com.centraldungeon.registrations;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.registrations.dto.RejectRegistrationRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Applications to a table, from both ends: the applicant applies and reads their own list, the
 * master reads the queue and answers it.
 *
 * <p>No class-level {@code @RequestMapping}: the paths sit on two different resources on purpose -
 * a table's registrations hang off the table ({@code /game-tables/{tableId}/registrations}), while
 * acting on one addresses the registration itself ({@code /registrations/{id}/accept}).
 */
@RestController
public class RegistrationController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final RegistrationService registrationService;

    /**
     * @param registrationService the service that owns the application rules
     */
    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Applying to a table. The applicant is the actor from the token, never a body field (#121).
     *
     * @param tableId     the table applied to
     * @param request     the applicant's note, optional
     * @param currentUser the applicant, from the token
     * @return 201 with the application and its Location header. 409 if they already have an active
     *         one on this table (#28), or if the table is not accepting applications
     */
    @PostMapping("/api/v1/game-tables/{tableId}/registrations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RegistrationResponse> apply(
            @PathVariable String tableId,
            @Valid @RequestBody CreateRegistrationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        RegistrationResponse created = registrationService.apply(tableId, currentUser.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/game-tables/" + tableId + "/registrations/" + created.id()))
                .body(created);
    }

    /**
     * Candidates only, FIFO - the queue the master's "Candidatos" tab reads (#28).
     *
     * @param tableId     the table
     * @param pageable    page and size; the FIFO order is fixed and not a caller's choice
     * @param currentUser the actor, from the token; the service checks they run the table
     * @return 200 with one page of pending candidates, oldest first
     */
    @GetMapping("/api/v1/game-tables/{tableId}/registrations")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<RegistrationResponse> listCandidates(
            @PathVariable String tableId,
            @PageableDefault(sort = {"createdAt", "id"}) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.listCandidatesForTable(tableId, currentUser.userId(), pageable);
    }

    /**
     * A master accepting a candidate.
     *
     * <p>Taking the last seat auto-rejects the candidates still queued (#34), and everyone affected
     * is notified - so one call can produce several notifications.
     *
     * @param registrationId the application to accept
     * @param currentUser    the actor, from the token; the service checks they run the table
     * @return 200 with the application, now a Player. 409 if the table is already full
     */
    @PostMapping("/api/v1/registrations/{registrationId}/accept")
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse accept(@PathVariable String registrationId, @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.accept(registrationId, currentUser.userId());
    }

    /**
     * A master turning down a candidate, with a reason that reaches them.
     *
     * @param registrationId the application to reject
     * @param request        the justification, required
     * @param currentUser    the actor, from the token; the service checks they run the table
     * @return 200 with the application, now Rejected
     */
    @PostMapping("/api/v1/registrations/{registrationId}/reject")
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse reject(
            @PathVariable String registrationId,
            @Valid @RequestBody RejectRegistrationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.reject(registrationId, currentUser.userId(), request);
    }

    /**
     * The applicant withdrawing their own pending application.
     *
     * <p>It is the way out R4's clash notice needs to leave open (#178): a notification saying two
     * of your tables now fall at the same hour is only useful if you can do something about it.
     *
     * <p>The path addresses the registration and the actor comes from the token, so there is no way
     * to spell "withdraw somebody else's" (#121) - the service still checks, because a path that
     * cannot express it is not the same as a rule that is enforced.
     *
     * @param registrationId the application to withdraw
     * @param currentUser    the applicant, from the token
     * @return nothing - 204. 403 when the application is somebody else's, 409 once it is no longer
     *         pending
     */
    @DeleteMapping("/api/v1/registrations/{registrationId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable String registrationId, @AuthenticationPrincipal CurrentUser currentUser) {
        registrationService.withdraw(registrationId, currentUser.userId());
    }

    /**
     * /my/applications - everything the actor applied to, whatever came of it.
     *
     * @param pageable    page, size and sort; newest first
     * @param currentUser the actor, from the token. There is no way to ask for somebody else's
     *                    applications: the id is not a parameter (#121)
     * @return 200 with one page of their applications
     */
    @GetMapping("/api/v1/registrations/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<RegistrationResponse> listMine(
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.listMine(currentUser.userId(), pageable);
    }
}
