package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.service.QueryAuthorisationService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class GetUserProfilesHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;
    private final QueryAuthorisationService authorizationHandler;

    public GetUserProfilesHandler(UserProfileQueryRepository userProfileQueryRepository,
                                   QueryAuthorisationService authorizationHandler) {
        this.userProfileQueryRepository = userProfileQueryRepository;
        this.authorizationHandler = authorizationHandler;
    }

    public List<UserProfileSummaryView> handle(String userEntraObjectId, AuthenticatedEntraUser authenticatedUser) {

        AuthenticatedUserProfile actorProfile = authenticatedUser.activeProfile();
        if (actorProfile == null) {
            throw new InvalidActorContextException(
                    "Actor has no active profile: " + authenticatedUser.entraOid());
        }

        if (actorProfile.userType() == null) {
            throw new InvalidActorContextException("Actor user type cannot be determined");
        }

        // Fetch all profiles for target user
        List<UserProfileSummaryView> allProfiles = userProfileQueryRepository
                .findAllProfilesByUserEntraObjectId(userEntraObjectId)
                .orElse(List.of());

        // Filter by authorization rules
        return allProfiles.stream()
                .filter(profile -> authorizationHandler.canViewProfile(actorProfile, profile.userType(), profile.firmId()))
                .collect(Collectors.toList());
    }
}
