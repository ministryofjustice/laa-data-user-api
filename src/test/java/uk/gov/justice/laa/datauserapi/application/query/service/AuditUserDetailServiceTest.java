package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.FirmDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditUserDetailServiceTest {

    private static final String TARGET_OID = UUID.randomUUID().toString();
    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock
    private UserAccountQueryService queryService;
    @Mock
    private AuditQueryRepository auditRepository;
    @Mock
    private UserViewMapper userViewMapper;

    private AuditUserDetailService service;

    @BeforeEach
    void setUp() {
        service = new AuditUserDetailService(queryService, auditRepository, new CanViewProfilePolicy(),
                userViewMapper, new AuditQueryMapper());
    }

    @Test
    void returnsAccountVisibleProfilesAndHistory() {
        UserProfileDto internal = profile(UserType.INTERNAL, null);
        UserProfileDto external = profile(UserType.EXTERNAL, FIRM_ID);
        EntraUserDto target = target(internal, external);
        UserProfileDetailView externalView = mock(UserProfileDetailView.class);
        LocalDateTime changed = LocalDateTime.now();
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_OID)).thenReturn(target);
        when(userViewMapper.mapToProfileDetailView(target, external)).thenReturn(externalView);
        when(auditRepository.findStatusHistory(TARGET_OID, PageRequest.of(0, 500))).thenReturn(List.of(
                new AuditStatusHistoryRow(TARGET_OID, UserAccountAuditStatus.DEACTIVATED, changed, "actor",
                        "CyberRisk", "Cyber risk"),
                new AuditStatusHistoryRow(TARGET_OID, UserAccountAuditStatus.ACTIVATED, changed.minusDays(1),
                        "actor", null, null)));

        AuditUserDetailView detail = service.getDetail(new AuditUserDetailQuery(TARGET_OID),
                internalActor(Permission.VIEW_EXTERNAL_USER));

        assertThat(detail.profiles()).containsExactly(externalView);
        verify(userViewMapper, never()).mapToProfileDetailView(target, internal);
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
    void returnsNoProfilesWhenNoneAreVisibleToTheActor() {
        EntraUserDto target = target(profile(UserType.INTERNAL, null));
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_OID)).thenReturn(target);
        when(auditRepository.findStatusHistory(any(), any())).thenReturn(List.of());

        AuditUserDetailView detail = service.getDetail(new AuditUserDetailQuery(TARGET_OID),
                internalActor(Permission.VIEW_EXTERNAL_USER));

        assertThat(detail.profiles()).isEmpty();
        assertThat(detail.entraUser().profileCount()).isZero();
        assertThat(detail.accountStatusHistory()).isEmpty();
    }

    @Test
    void returnsAtMostOneHundredVisibleProfilesActiveProfileFirst() {
        List<UserProfileDto> profiles = new java.util.ArrayList<>();
        for (int i = 0; i < 105; i++) {
            profiles.add(profile(UserType.EXTERNAL, FIRM_ID));
        }
        UserProfileDto active = UserProfileDto.builder().id(UUID.randomUUID()).userType(UserType.EXTERNAL)
                .activeProfile(true).firm(FirmDto.builder().id(FIRM_ID).build()).build();
        profiles.add(active);
        EntraUserDto target = target(profiles.toArray(UserProfileDto[]::new));
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_OID)).thenReturn(target);
        when(auditRepository.findStatusHistory(any(), any())).thenReturn(List.of());
        when(userViewMapper.mapToProfileDetailView(any(), any())).thenReturn(mock(UserProfileDetailView.class));

        AuditUserDetailView detail = service.getDetail(new AuditUserDetailQuery(TARGET_OID),
                internalActor(Permission.VIEW_EXTERNAL_USER));

        assertThat(detail.profiles()).hasSize(100);
        assertThat(detail.entraUser().profileCount()).isEqualTo(106);
        verify(userViewMapper).mapToProfileDetailView(target, active);
    }

    @Test
    void capsHistoryAtTheDocumentedMaximum() {
        EntraUserDto target = target();
        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_OID)).thenReturn(target);
        when(auditRepository.findStatusHistory(any(), any())).thenReturn(List.of());

        service.getDetail(new AuditUserDetailQuery(TARGET_OID), internalActor(Permission.VIEW_EXTERNAL_USER));

        ArgumentCaptor<PageRequest> page = ArgumentCaptor.forClass(PageRequest.class);
        verify(auditRepository).findStatusHistory(any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(500);
    }

    private static EntraUserDto target(UserProfileDto... profiles) {
        return EntraUserDto.builder()
                .entraOid(TARGET_OID)
                .invitationStatus(InvitationStatus.VERIFICATION_SUCCESS)
                .userProfiles(Set.of(profiles))
                .build();
    }

    private static UserProfileDto profile(UserType type, UUID firmId) {
        return UserProfileDto.builder()
                .id(UUID.randomUUID())
                .userType(type)
                .firm(firmId == null ? null : FirmDto.builder().id(firmId).build())
                .build();
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
