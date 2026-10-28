package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.service.QueryAuthorisationService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;
    private final QueryAuthorisationService authorisationHandler;
    private final ModelMapper modelMapper;

    public GetActiveProfileHandler(UserProfileQueryRepository userProfileQueryRepository,
                                    QueryAuthorisationService authorisationHandler,
                                    ModelMapper modelMapper) {
        this.userProfileQueryRepository = userProfileQueryRepository;
        this.authorisationHandler = authorisationHandler;
        this.modelMapper = modelMapper;
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
        UserProfileDto targetProfile = userProfileQueryRepository
                .findActiveProfileWithDetailsAndRoles(userEntraObjectId)
                .map(profile -> modelMapper.map(profile, UserProfileDto.class))
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
