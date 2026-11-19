package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditAccountRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditProfileRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditProfileMapper;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountVisibilityContextFactory;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAuditUserPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserAccessRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Assembles an account's audit detail. Account and profile visibility are decided from lightweight rows; only the
 * visible profiles that are returned (at most {@value #MAX_PROFILES}) have their firm, roles and offices loaded.
 * The account-visibility check is repeated here against the rows used for the response, because the handler-level
 * check read the account in a separate transaction.
 */
@Service
@Transactional(readOnly = true)
public class AuditUserDetailService {

    static final int MAX_HISTORY_ITEMS = 500;
    static final int MAX_PROFILES = 100;

    // Active profile first, then by id, so the profiles kept under MAX_PROFILES are stable between calls.
    private static final Comparator<AuditProfileRow> PROFILE_ORDER = Comparator
            .comparing(AuditProfileRow::activeProfile, Comparator.reverseOrder())
            .thenComparing(AuditProfileRow::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private final AuditUserAccessRepository accessRepository;
    private final AuditQueryRepository auditRepository;
    private final CanViewAuditUserPolicy accountPolicy;
    private final UserAccountVisibilityContextFactory contextFactory;
    private final CanViewProfilePolicy profilePolicy;
    private final AuditProfileMapper profileMapper;
    private final UserViewMapper userViewMapper;
    private final AuditQueryMapper auditMapper;

    public AuditUserDetailService(AuditUserAccessRepository accessRepository,
                                  AuditQueryRepository auditRepository,
                                  CanViewAuditUserPolicy accountPolicy,
                                  UserAccountVisibilityContextFactory contextFactory,
                                  CanViewProfilePolicy profilePolicy,
                                  AuditProfileMapper profileMapper,
                                  UserViewMapper userViewMapper,
                                  AuditQueryMapper auditMapper) {
        this.accessRepository = accessRepository;
        this.auditRepository = auditRepository;
        this.accountPolicy = accountPolicy;
        this.contextFactory = contextFactory;
        this.profilePolicy = profilePolicy;
        this.profileMapper = profileMapper;
        this.userViewMapper = userViewMapper;
        this.auditMapper = auditMapper;
    }

    public AuditUserDetailView getDetail(AuditUserDetailQuery query, AuthenticatedEntraUser actor) {
        String oid = query.userEntraObjectId();
        AuditAccountRow account = accessRepository.findAccount(oid)
                .orElseThrow(() -> new UserNotFoundException(oid));
        List<AuditProfileRow> profileRows = accessRepository.findProfileRows(oid);
        requireAccountVisible(account, profileRows, actor);

        List<AuditProfileRow> visible = profileRows.stream()
                .filter(profile -> canViewProfile(profile, actor))
                .sorted(PROFILE_ORDER)
                .toList();
        EntraUserDto accountDto = profileMapper.toAccountDto(account);
        List<UserProfileDetailView> returnedProfiles = loadProfileDetails(visible).stream()
                .map(profile -> userViewMapper.mapToProfileDetailView(accountDto, profile))
                .toList();
        List<AuditStatusHistoryRow> history = auditRepository.findStatusHistory(
                account.entraOid(), PageRequest.of(0, MAX_HISTORY_ITEMS));
        return auditMapper.toDetailView(accountDto, returnedProfiles, visible.size(), history);
    }

    private List<UserProfileDto> loadProfileDetails(List<AuditProfileRow> visible) {
        List<UUID> selectedIds = visible.stream().limit(MAX_PROFILES).map(AuditProfileRow::id).toList();
        if (selectedIds.isEmpty()) {
            return List.of();
        }
        return profileMapper.toOrderedProfileDtos(accessRepository.findProfilesWithDetails(selectedIds), selectedIds);
    }

    private void requireAccountVisible(AuditAccountRow account, List<AuditProfileRow> profileRows,
                                       AuthenticatedEntraUser actor) {
        PolicyDecision decision = accountPolicy.evaluate(
                contextFactory.fromFacts(account.entraOid(), account.multiFirmUser(), profileRows), actor);
        if (!decision.granted()) {
            throw new AuthorizationDeniedException("Access denied", new PolicyAuthorizationDenial(decision.reason()));
        }
    }

    private boolean canViewProfile(AuditProfileRow profile, AuthenticatedEntraUser actor) {
        ProfileVisibilityContext context = new ProfileVisibilityContext(
                true, profile.id(), profile.userType(), profile.firmId());
        return profilePolicy.evaluate(context, actor).granted();
    }
}
