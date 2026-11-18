package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.mapper.ProfileTrialReadMapper;
import uk.gov.justice.laa.datauserapi.application.query.profiletrial.ProfileTrialQuery;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialRoleViewMapper;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryPage;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput;
import java.util.Comparator;

@Service
@Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
public class ProfileTrialQueryService {
    private final ProfileTrialReadMapper mapper;
    private final ProfileTrialRoleViewMapper roleMapper;

    public ProfileTrialQueryService(ProfileTrialReadMapper mapper,
                                    ProfileTrialRoleViewMapper roleMapper) {
        this.mapper = mapper;
        this.roleMapper = roleMapper;
    }

    public UserProfileSummaryPage search(ProfileTrialQuery query) {
        return mapper.search(query);
    }

    public UserProfileDetailView detail(ProfileTrialQuery query) {
        return mapper.detail(query);
    }

    public AppRoleViewList assignable(ProfileTrialQuery query) {
        return new AppRoleViewList(query.plan().decision().queryFilters().roleIds().stream()
                .map(query.plan().input().roles()::get)
                .sorted(Comparator.comparing(ProfileTrialInput.Role::appName)
                        .thenComparingInt(ProfileTrialInput.Role::ordinal).thenComparing(ProfileTrialInput.Role::id))
                .map(roleMapper::toView).toList());
    }
}
