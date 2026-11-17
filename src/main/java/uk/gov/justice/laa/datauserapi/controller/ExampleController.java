package uk.gov.justice.laa.datauserapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.service.UserProfileService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

@RestController
@RequestMapping("/api/v1")
@RequiresReadScope
public class ExampleController {

    private final UserProfileService userProfileService;

    public ExampleController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * Returns the caller's profile derived from the validated JWT {@code oid} claim.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDetailView> me(@AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        return ResponseEntity.ok(userProfileService.getProfile(authenticatedUser));
    }
}
