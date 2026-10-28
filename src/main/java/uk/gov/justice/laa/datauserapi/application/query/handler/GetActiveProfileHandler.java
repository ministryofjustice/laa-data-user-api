package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.service.QueryAuthorisationService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;


@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;
    private final QueryAuthorisationService authorisationHandler;

    public GetActiveProfileHandler(UserProfileQueryRepository userProfileQueryRepository,
                                    QueryAuthorisationService authorisationHandler) {
        this.userProfileQueryRepository = userProfileQueryRepository;
        this.authorisationHandler = authorisationHandler;
    }

    public UserProfileDetailView handle(String userEntraObjectId, AuthenticatedEntraUser authenticatedUser) {

        AuthenticatedUserProfile actorProfile = authenticatedUser.activeProfile();
        if (actorProfile == null) {
            throw new InvalidActorContextException(
                    "Actor has no active profile: " + authenticatedUser.entraOid());
        }

        if (actorProfile.userType() == null) {
            throw new InvalidActorContextException("Actor user type cannot be determined");
        }

        // Fetch target user's active profile
        UserProfile targetProfile = userProfileQueryRepository
                .findActiveProfileWithDetailsAndRoles(userEntraObjectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active profile not found for user: " + userEntraObjectId));

        // Check authorization
        String targetFirmId = targetProfile.getFirm() != null ? targetProfile.getFirm().getId().toString() : null;
        if (!authorisationHandler.canViewProfile(actorProfile, targetProfile.getUserType(), targetFirmId)) {
            throw new ResourceNotFoundException(
                    "Active profile not found for user: " + userEntraObjectId);
        }

        return new GetActiveProfileQueryMapper().mapProfileToDetailView(targetProfile);
    }
}
