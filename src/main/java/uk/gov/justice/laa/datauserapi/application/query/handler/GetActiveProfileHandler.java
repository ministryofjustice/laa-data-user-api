package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.Permission;


@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;
    private final UserAccountQueryService queryService;

    public GetActiveProfileHandler(UserProfileQueryRepository userProfileQueryRepository,
                                    UserAccountQueryService queryService) {
        this.userProfileQueryRepository = userProfileQueryRepository;
        this.queryService = queryService;
    }

    public UserProfileDetailView handle(GetActiveProfileQuery query) {
        // Validate actor has active profile
        EntraUserDto actor = queryService.findByEntraOid(query.actorOid());
        UserProfile actorProfile = actor.getUserProfiles().stream()
                .filter(UserProfile::isActiveProfile)
                .findFirst()
                .orElseThrow(() -> new InvalidActorContextException(
                        "Actor has no active profile: " + query.actorOid()));

        if (actorProfile.getUserType() == null) {
            throw new InvalidActorContextException("Actor user type cannot be determined");
        }

        // Fetch target user's active profile
        UserProfile activeProfile = userProfileQueryRepository
                .findActiveProfileWithDetailsAndRoles(query.userEntraObjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active profile not found for user: " + query.userEntraObjectId()));

        // Check authorization
        if (!canViewProfile(actorProfile, activeProfile)) {
            throw new ResourceNotFoundException(
                    "Active profile not found for user: " + query.userEntraObjectId());
        }

        return new GetActiveProfileQueryMapper().mapProfileToDetailView(activeProfile);
    }

    private boolean canViewProfile(UserProfile actorProfile, UserProfile targetProfile) {
        // INTERNAL actor can view INTERNAL profiles
        if (actorProfile.getUserType() == UserType.INTERNAL 
                && targetProfile.getUserType() == UserType.INTERNAL) {
            return true;
        }

        // EXTERNAL actor can view profiles in their own firm
        if (actorProfile.getUserType() == UserType.EXTERNAL 
                && actorProfile.getFirm() != null
                && targetProfile.getFirm() != null
                && actorProfile.getFirm().getId().equals(targetProfile.getFirm().getId())) {
            return true;
        }

        // INTERNAL actor with VIEW_EXTERNAL_USER can view EXTERNAL profiles
        boolean canViewExternalUsers = actorProfile.getAppRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);

        return actorProfile.getUserType() == UserType.INTERNAL 
                && canViewExternalUsers 
                && targetProfile.getUserType() == UserType.EXTERNAL;
    }
}
