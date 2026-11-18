package uk.gov.justice.laa.datauserapi.application.query.controller;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.handler.ProfileTrialQueryHandler;
import uk.gov.justice.laa.datauserapi.application.query.profiletrial.ProfileTrialQuery;
import uk.gov.justice.laa.datauserapi.contracts.dto.ProfileSearchCriteria;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialOperation;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialRequest;

@RestController
@RequestMapping("/api/v1/queries/user-profiles")
@RequiresReadScope
public class ProfileTrialQueryController {
    private final ProfileTrialQueryHandler handler;

    public ProfileTrialQueryController(ProfileTrialQueryHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public UserProfileSummaryPage search(@Valid @ModelAttribute ProfileSearchCriteria criteria,
                                        @RequestParam MultiValueMap<String, String> parameters,
                                        @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        return handler.handle(query(ProfileTrialOperation.SEARCH, null,
                criteria.withSortBy(parameters.getOrDefault("sort_by", java.util.List.of())), actor), actor).page();
    }

    @GetMapping("/{userProfileId}")
    public UserProfileDetailView detail(@PathVariable UUID userProfileId,
                                        @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        return handler.handle(query(ProfileTrialOperation.DETAIL, userProfileId, null, actor), actor).detail();
    }

    @GetMapping("/{userProfileId}/assignable-roles")
    public AppRoleViewList assignable(@PathVariable UUID userProfileId,
                                      @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        return handler.handle(query(ProfileTrialOperation.ASSIGNABLE, userProfileId, null, actor), actor).roles();
    }

    private static ProfileTrialQuery query(ProfileTrialOperation operation, UUID id, ProfileSearchCriteria criteria,
                                           AuthenticatedEntraUser actor) {
        return new ProfileTrialQuery(new ProfileTrialRequest(operation, id, Set.of(), Map.of(), actor), criteria, null);
    }
}
