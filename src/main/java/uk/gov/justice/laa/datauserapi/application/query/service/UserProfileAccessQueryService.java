package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserProfileAccessTarget;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileAccessQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileAccessQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.entity.AppRole;

/**
 * Serves the roles, applications and offices of one user profile. Callers are authorised before this service
 * is reached: the query messages are guarded by {@code CanViewProfilePolicy}.
 */
@Service
@Transactional(readOnly = true)
public class UserProfileAccessQueryService {

    static final int MAX_APP_ROLES = 500;
    static final int MAX_APPS = 100;
    static final int MAX_OFFICES = 100;

    private final UserProfileAccessQueryRepository repository;
    private final UserProfileAccessQueryMapper mapper;

    public UserProfileAccessQueryService(UserProfileAccessQueryRepository repository,
                                         UserProfileAccessQueryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public AppRoleViewList getAppRoles(UUID userProfileId, UUID appId, UUID appEntraClientId) {
        String clientId = appEntraClientId == null ? null : appEntraClientId.toString();
        List<AppRole> roles = repository.findAppRolesByProfileId(userProfileId, appId, clientId, limit(MAX_APP_ROLES));
        return new AppRoleViewList(mapper.toAppRoleViews(roles));
    }

    public AppViewList getApps(UUID userProfileId) {
        List<AppView> apps = repository.findEnabledAppsByProfileId(userProfileId, limit(MAX_APPS));
        return new AppViewList(apps);
    }

    public OfficeViewList getOffices(UUID userProfileId) {
        UserProfileAccessTarget target = repository.findAccessTargetById(userProfileId).orElse(null);
        if (target == null || target.firmId() == null) {
            return new OfficeViewList(List.of());
        }
        List<OfficeView> offices = target.unrestrictedOfficeAccess()
                ? repository.findOfficesByFirmId(target.firmId(), limit(MAX_OFFICES))
                : repository.findAssignedOfficesByProfileId(userProfileId, target.firmId(), limit(MAX_OFFICES));
        return new OfficeViewList(offices);
    }

    private Pageable limit(int max) {
        return PageRequest.ofSize(max);
    }
}
