package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditAccountRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditProfileRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditProfileMapper;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountVisibilityContextFactory;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAccountPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAuditUserPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserAccessRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditUserDetailServiceTest {

    private static final String TARGET_OID = UUID.randomUUID().toString();
    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock
    private AuditUserAccessRepository accessRepository;
    @Mock
    private AuditQueryRepository auditRepository;
    @Mock
    private UserViewMapper userViewMapper;

    private AuditUserDetailService service;

    @BeforeEach
    void setUp() {
        service = new AuditUserDetailService(accessRepository, auditRepository,
                new CanViewAuditUserPolicy(new CanViewAccountPolicy()), new UserAccountVisibilityContextFactory(),
                new CanViewProfilePolicy(), new AuditProfileMapper(), userViewMapper, new AuditQueryMapper());
    }

    @Test
    void returnsAccountVisibleProfilesAndHistory() {
        AuditProfileRow internal = row(UserType.INTERNAL, null, false);
        AuditProfileRow external = row(UserType.EXTERNAL, FIRM_ID, false);
        UserProfileDetailView externalView = mock(UserProfileDetailView.class);
        stubAccount(false, internal, external);
        when(accessRepository.findProfilesWithDetails(List.of(external.id())))
                .thenReturn(List.of(UserProfile.builder().id(external.id()).userType(UserType.EXTERNAL).build()));
        when(userViewMapper.mapToProfileDetailView(any(), any())).thenReturn(externalView);
        LocalDateTime changed = LocalDateTime.now();
        when(auditRepository.findStatusHistory(TARGET_OID, PageRequest.of(0, 500))).thenReturn(List.of(
                new AuditStatusHistoryRow(TARGET_OID, UserAccountAuditStatus.DEACTIVATED, changed, "actor",
                        "CyberRisk", "Cyber risk"),
                new AuditStatusHistoryRow(TARGET_OID, UserAccountAuditStatus.ACTIVATED, changed.minusDays(1),
                        "actor", null, null)));

        AuditUserDetailView detail = service.getDetail(new AuditUserDetailQuery(TARGET_OID),
                internalActor(Permission.VIEW_EXTERNAL_USER));

        assertThat(detail.profiles()).containsExactly(externalView);
        assertThat(detail.entraUser().userEntraObjectId()).isEqualTo(TARGET_OID);
        assertThat(detail.entraUser().profileCount()).isEqualTo(1);
        assertThat(detail.entraUser().entraInviteStatus()).isEqualTo("VerificationSuccess");
        assertThat(detail.entraUser().lastLoginDate()).isNull();
        assertThat(detail.accountStatusHistory()).hasSize(2);
        assertThat(detail.accountStatusHistory().getFirst().statusChange()).isEqualTo(UserAccountAuditStatus.DEACTIVATED);
        assertThat(detail.accountStatusHistory().getFirst().deactivateReasonLabel()).isEqualTo("Cyber risk");
        assertThat(detail.accountStatusHistory().getLast().deactivateReason()).isNull();
    }

    @Test
    void unknownAccountIsNotFound() {
        when(accessRepository.findAccount(TARGET_OID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(new AuditUserDetailQuery(TARGET_OID),
                internalActor(Permission.VIEW_EXTERNAL_USER)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deniesWhenAccessWasRevokedAfterTheAuthorisationCheck() {
        // The handler-level check passed on an earlier read; the account is no longer visible when it is read here.
        stubAccount(false, row(UserType.EXTERNAL, FIRM_ID, false));

        assertThatThrownBy(() -> service.getDetail(new AuditUserDetailQuery(TARGET_OID),
                internalActor(Permission.VIEW_AUDIT_TABLE)))
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, denied ->
                        assertThat(denied.getAuthorizationResult())
                                .isEqualTo(new PolicyAuthorizationDenial(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE)));

        verifyNoInteractions(auditRepository, userViewMapper);
    }

    @Test
    void returnsNoProfilesAndLoadsNoDetailsWhenNoneAreVisibleToTheActor() {
        // An account without profiles is treated as external only when multi-firm, so it is visible to this actor.
        stubAccount(true);
        when(auditRepository.findStatusHistory(any(), any())).thenReturn(List.of());

        AuditUserDetailView detail = service.getDetail(new AuditUserDetailQuery(TARGET_OID),
                internalActor(Permission.VIEW_EXTERNAL_USER));

        assertThat(detail.profiles()).isEmpty();
        assertThat(detail.entraUser().profileCount()).isZero();
        assertThat(detail.accountStatusHistory()).isEmpty();
        verify(accessRepository, never()).findProfilesWithDetails(any());
    }

    @Test
    void loadsDetailsForAtMostOneHundredVisibleProfilesActiveProfileFirst() {
        List<AuditProfileRow> rows = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            rows.add(row(UserType.EXTERNAL, FIRM_ID, false));
        }
        AuditProfileRow active = row(UserType.EXTERNAL, FIRM_ID, true);
        rows.add(active);
        stubAccount(false, rows.toArray(AuditProfileRow[]::new));
        when(accessRepository.findProfilesWithDetails(any())).thenReturn(List.of());
        when(auditRepository.findStatusHistory(any(), any())).thenReturn(List.of());

        AuditUserDetailView detail = service.getDetail(new AuditUserDetailQuery(TARGET_OID),
                internalActor(Permission.VIEW_EXTERNAL_USER));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(accessRepository).findProfilesWithDetails(ids.capture());
        assertThat(ids.getValue()).hasSize(100).contains(active.id());
        assertThat(ids.getValue().iterator().next()).isEqualTo(active.id());
        assertThat(detail.entraUser().profileCount()).isEqualTo(106);
    }

    @Test
    void capsHistoryAtTheDocumentedMaximum() {
        stubAccount(true);
        when(auditRepository.findStatusHistory(any(), any())).thenReturn(List.of());

        service.getDetail(new AuditUserDetailQuery(TARGET_OID), internalActor(Permission.VIEW_EXTERNAL_USER));

        ArgumentCaptor<PageRequest> page = ArgumentCaptor.forClass(PageRequest.class);
        verify(auditRepository).findStatusHistory(any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(500);
    }

    private void stubAccount(boolean multiFirm, AuditProfileRow... profiles) {
        when(accessRepository.findAccount(TARGET_OID)).thenReturn(Optional.of(new AuditAccountRow(
                TARGET_OID, "a@b.test", "First", "Last", UserAccountStatus.ACTIVE, multiFirm, false, null, null, null,
                InvitationStatus.VERIFICATION_SUCCESS)));
        when(accessRepository.findProfileRows(TARGET_OID)).thenReturn(List.of(profiles));
    }

    private static AuditProfileRow row(UserType type, UUID firmId, boolean active) {
        return new AuditProfileRow(UUID.randomUUID(), active, type, firmId);
    }

    private static AuthenticatedEntraUser internalActor(Permission... permissions) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false,
                null, null, "Role", true, null, Set.of(permissions));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(Set.of(role))
                .build();
        return AuthenticatedEntraUser.builder().entraOid("actor").active(true).activeProfile(profile).build();
    }
}
