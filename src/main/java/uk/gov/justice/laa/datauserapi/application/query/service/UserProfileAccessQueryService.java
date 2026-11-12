package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileAccessDenialReason;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserProfileAccessTarget;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileAccessQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileAccessQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.AppRole;

/**
 * Serves the roles, applications and offices of one user profile to callers who are allowed to view that profile.
 * A missing profile and a profile the actor cannot view are both denied identically.
 */
@Service
@Transactional(readOnly = true)
public class UserProfileAccessQueryService {

    static final int MAX_APP_ROLES = 500;
    static final int MAX_APPS = 100;
    static final int MAX_OFFICES = 100;

    private final UserProfileAccessQueryRepository repository;
    private final QueryAuthorisationService authorisationService;
    private final UserProfileAccessQueryMapper mapper;

    public UserProfileAccessQueryService(UserProfileAccessQueryRepository repository,
                                         QueryAuthorisationService authorisationService,
                                         UserProfileAccessQueryMapper mapper) {
        this.repository = repository;
        this.authorisationService = authorisationService;
        this.mapper = mapper;
    }

    public AppRoleViewList getAppRoles(UUID userProfileId, UUID appId, UUID appEntraClientId,
                                       AuthenticatedEntraUser actor) {
        requireVisibleProfile(userProfileId, actor);
        String clientId = appEntraClientId == null ? null : appEntraClientId.toString();
        List<AppRole> roles = repository.findAppRolesByProfileId(userProfileId, appId, clientId, limit(MAX_APP_ROLES));
        return new AppRoleViewList(mapper.toAppRoleViews(roles));
    }

    public AppViewList getApps(UUID userProfileId, AuthenticatedEntraUser actor) {
        requireVisibleProfile(userProfileId, actor);
        List<AppView> apps = repository.findEnabledAppsByProfileId(userProfileId, limit(MAX_APPS));
        return new AppViewList(apps);
    }

    public OfficeViewList getOffices(UUID userProfileId, AuthenticatedEntraUser actor) {
        UserProfileAccessTarget target = requireVisibleProfile(userProfileId, actor);
        if (target.firmId() == null) {
            return new OfficeViewList(List.of());
        }
        List<OfficeView> offices = target.unrestrictedOfficeAccess()
                ? repository.findOfficesByFirmId(target.firmId(), limit(MAX_OFFICES))
                : repository.findAssignedOfficesByProfileId(userProfileId, target.firmId(), limit(MAX_OFFICES));
        return new OfficeViewList(offices);
    }

    private UserProfileAccessTarget requireVisibleProfile(UUID userProfileId, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = authorisationService.requireActiveProfile(actor);
        UserProfileAccessTarget target = repository.findAccessTargetById(userProfileId).orElse(null);
        if (target == null) {
            throw deniedAccess(ProfileAccessDenialReason.PROFILE_NOT_FOUND);
        }
        String targetFirmId = target.firmId() == null ? null : target.firmId().toString();
        if (!authorisationService.canViewProfile(actorProfile, target.userType(), targetFirmId)) {
            throw deniedAccess(ProfileAccessDenialReason.PROFILE_NOT_VISIBLE);
        }
        return target;
    }

    private AccessDeniedException deniedAccess(ProfileAccessDenialReason reason) {
        return new AccessDeniedException("User profile access denied: " + reason);
    }

    private Pageable limit(int max) {
        return PageRequest.ofSize(max);
    }
}
