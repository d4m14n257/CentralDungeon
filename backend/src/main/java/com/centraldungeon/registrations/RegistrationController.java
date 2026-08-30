package com.centraldungeon.registrations;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.registrations.dto.RejectRegistrationRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

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

    /** Candidates only, FIFO - the queue the master's "Candidatos" tab reads (#28). */
    @GetMapping("/api/v1/game-tables/{tableId}/registrations")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<RegistrationResponse> listCandidates(
            @PathVariable String tableId, Pageable pageable, @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.listCandidatesForTable(tableId, currentUser.userId(), pageable);
    }

    @PostMapping("/api/v1/registrations/{registrationId}/accept")
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse accept(@PathVariable String registrationId, @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.accept(registrationId, currentUser.userId());
    }

    @PostMapping("/api/v1/registrations/{registrationId}/reject")
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse reject(
            @PathVariable String registrationId,
            @Valid @RequestBody RejectRegistrationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.reject(registrationId, currentUser.userId(), request);
    }

    @GetMapping("/api/v1/registrations/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<RegistrationResponse> listMine(Pageable pageable, @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.listMine(currentUser.userId(), pageable);
    }
}
