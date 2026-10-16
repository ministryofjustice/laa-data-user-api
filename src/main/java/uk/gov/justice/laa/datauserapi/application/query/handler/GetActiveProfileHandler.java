package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;


@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;
    private final UserAccountQueryService queryService;
    private final QueryProfileAuthorisationHandler authorisationService;

    public GetActiveProfileHandler(UserProfileQueryRepository userProfileQueryRepository,
                                    UserAccountQueryService queryService,
                                    QueryProfileAuthorisationHandler authorisationService) {
        this.userProfileQueryRepository = userProfileQueryRepository;
        this.queryService = queryService;
        this.authorisationService = authorisationService;
    }

    public UserProfileDetailView handle(String userEntraObjectId, AuthenticatedEntraUser authenticatedUser) {
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

        // Fetch target user's active profile
        UserProfile activeProfile = userProfileQueryRepository
                .findActiveProfileWithDetailsAndRoles(userEntraObjectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active profile not found for user: " + userEntraObjectId));

        // Check authorization
        if (!authorisationService.handle(actorProfile, activeProfile)) {
            throw new ResourceNotFoundException(
                    "Active profile not found for user: " + userEntraObjectId);
        }

        return new GetActiveProfileQueryMapper().mapProfileToDetailView(activeProfile);
    }
}
