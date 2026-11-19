package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;

import java.util.List;

/**
 * Assembles an account's audit detail. The caller's right to see the account is decided before this runs; here each
 * profile is additionally filtered so profiles outside the caller's visibility are never returned.
 */
@Service
@Transactional(readOnly = true)
public class AuditUserDetailService {

    static final int MAX_HISTORY_ITEMS = 500;

    private final UserAccountQueryService queryService;
    private final AuditQueryRepository auditRepository;
    private final CanViewProfilePolicy profilePolicy;
    private final UserViewMapper userViewMapper;
    private final AuditQueryMapper auditMapper;

    public AuditUserDetailService(UserAccountQueryService queryService,
                                  AuditQueryRepository auditRepository,
                                  CanViewProfilePolicy profilePolicy,
                                  UserViewMapper userViewMapper,
                                  AuditQueryMapper auditMapper) {
        this.queryService = queryService;
        this.auditRepository = auditRepository;
        this.profilePolicy = profilePolicy;
        this.userViewMapper = userViewMapper;
        this.auditMapper = auditMapper;
    }

    public AuditUserDetailView getDetail(AuditUserDetailQuery query, AuthenticatedEntraUser actor) {
        EntraUserDto target = queryService.findUserAccountSummaryByUserEntraObjectId(query.userEntraObjectId());
        List<UserProfileDetailView> visibleProfiles = target.getUserProfiles().stream()
                .filter(profile -> canViewProfile(profile, actor))
                .map(profile -> userViewMapper.mapToProfileDetailView(target, profile))
                .toList();
        List<AuditStatusHistoryRow> history = auditRepository.findStatusHistory(
                target.getEntraOid(), PageRequest.of(0, MAX_HISTORY_ITEMS));
        return auditMapper.toDetailView(target, visibleProfiles, history);
    }

    private boolean canViewProfile(UserProfileDto profile, AuthenticatedEntraUser actor) {
        ProfileVisibilityContext context = new ProfileVisibilityContext(
                true,
                profile.getId(),
                profile.getUserType(),
                profile.getFirm() == null ? null : profile.getFirm().getId());
        return profilePolicy.evaluate(context, actor).granted();
    }
}
