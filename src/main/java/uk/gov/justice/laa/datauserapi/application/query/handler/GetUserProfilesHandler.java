package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class GetUserProfilesHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;
    private final UserAccountQueryService queryService;
    private final QueryProfileAuthorisationHandler authorizationService;

    public GetUserProfilesHandler(UserProfileQueryRepository userProfileQueryRepository,
                                   UserAccountQueryService queryService,
                                   QueryProfileAuthorisationHandler authorizationService) {
        this.userProfileQueryRepository = userProfileQueryRepository;
        this.queryService = queryService;
        this.authorizationService = authorizationService;
    }

    public List<UserProfileSummaryView> handle(String userEntraObjectId, AuthenticatedEntraUser authenticatedUser) {
        // Validate actor has active profile
        EntraUserDto actor = queryService.findByEntraOid(authenticatedUser.entraOid());
        UserProfile actorProfile = actor.getUserProfiles().stream()
                .filter(UserProfile::isActiveProfile)
                .findFirst()
                .orElseThrow(() -> new InvalidActorContextException(
                        "Actor has no active profile: " + authenticatedUser.entraOid()));

        if (actorProfile.getUserType() == null) {
            throw new InvalidActorContextException("Actor user type cannot be determined");
        }

        // Fetch all profiles for target user
        List<UserProfileSummaryView> allProfiles = userProfileQueryRepository
                .findAllProfilesByUserEntraObjectId(userEntraObjectId)
                .orElse(List.of());

        // Filter by authorization rules
        return allProfiles.stream()
                .filter(profile -> authorizationService.handle(actorProfile, profile))
                .collect(Collectors.toList());
    }
}
