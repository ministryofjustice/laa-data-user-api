package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryuserprofilesbyentrauser.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class GetUserProfilesHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;
    private final UserAccountQueryService queryService;

    public GetUserProfilesHandler(UserProfileQueryRepository userProfileQueryRepository,
                                   UserAccountQueryService queryService) {
        this.userProfileQueryRepository = userProfileQueryRepository;
        this.queryService = queryService;
    }

    public List<UserProfileSummaryView> handle(GetUserProfilesQuery query) {
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

        // Fetch all profiles for target user
        List<UserProfileSummaryView> allProfiles = userProfileQueryRepository
                .findAllProfilesByUserEntraObjectId(query.userEntraObjectId())
                .orElse(List.of());

        // Filter by authorization rules
        return allProfiles.stream()
                .filter(profile -> canViewProfile(actorProfile, profile))
                .collect(Collectors.toList());
    }

    private boolean canViewProfile(UserProfile actorProfile, UserProfileSummaryView targetProfile) {
        // INTERNAL actor can view INTERNAL profiles
        if (actorProfile.getUserType() == UserType.INTERNAL 
                && targetProfile.userType() == UserType.INTERNAL) {
            return true;
        }

        // EXTERNAL actor can view profiles in their own firm
        if (actorProfile.getUserType() == UserType.EXTERNAL 
                && actorProfile.getFirm() != null
                && targetProfile.firmId() != null
                && actorProfile.getFirm().getId().toString().equals(targetProfile.firmId())) {
            return true;
        }

        // INTERNAL actor with VIEW_EXTERNAL_USER can view EXTERNAL profiles
        boolean canViewExternalUsers = actorProfile.getAppRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);

        return actorProfile.getUserType() == UserType.INTERNAL 
                && canViewExternalUsers 
                && targetProfile.userType() == UserType.EXTERNAL;
    }
}
