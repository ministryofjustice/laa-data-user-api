package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserView;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.UserType;

import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class GetUserAccountHandler {

    private final UserAccountQueryService queryService;
    private final UserViewMapper userViewMapper;

    public GetUserAccountHandler(
            UserAccountQueryService queryService,
            UserViewMapper userViewMapper) {
        this.queryService = queryService;
        this.userViewMapper = userViewMapper;
    }

    public UserView handle(GetUserAccountQuery query) {
        EntraUserDto actor = queryService.findByEntraOid(query.actorOid());

        UserProfile actorProfile = actor.getUserProfiles().stream()
                .filter(UserProfile::isActiveProfile)
                .findFirst()
                .orElseThrow(() -> new InvalidActorContextException(
                        "Actor has no active profile: " + query.actorOid()));

        if (actorProfile.getUserType() == null) {
            throw new InvalidActorContextException(
                    "Actor user type cannot be determined");
        }

        validateUuidFormat(query.userEntraObjectId(), "userEntraObjectId");
        EntraUserDto target = queryService.findUserAccountSummaryByUserEntraObjectId(
                        query.userEntraObjectId());

        if (!canView(actorProfile, target)) {
            throw new ResourceNotFoundException(
                    "User not found: " + query.userEntraObjectId());
        }

        return userViewMapper.mapToUserView(target);
    }

    private boolean canView(UserProfile actorProfile, EntraUserDto target) {
        if (actorProfile.getUserType() == UserType.INTERNAL
                && target.getUserProfiles().stream()
                .anyMatch(profile -> profile.getUserType() == UserType.INTERNAL)) {
            return true;
        }

        if (actorProfile.getUserType() == UserType.EXTERNAL
                && actorProfile.getFirm() != null
                && target.getUserProfiles().stream().anyMatch(profile ->
                profile.getFirm() != null
                        && profile.getFirm().getId().equals(actorProfile.getFirm().getId()))) {
            return true;
        }

        boolean canViewExternalUsers = actorProfile.getAppRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);

        return actorProfile.getUserType() == UserType.INTERNAL
                && canViewExternalUsers
                && target.getUserProfiles().stream()
                .anyMatch(profile -> profile.getUserType() == UserType.EXTERNAL);
    }

    private void validateUuidFormat(String value, String fieldName) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidUuidFormatException(
                    String.format("%s must match UUID format", fieldName));
        }
    }
}
