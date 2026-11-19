package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.application.query.dto.DeletedUserAuditRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.DeletedUsersAuditQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditDeletedUsersCriteria;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletedUsersAuditServiceTest {

    @Mock
    private AuditQueryRepository auditRepository;

    private DeletedUsersAuditService service;

    @BeforeEach
    void setUp() {
        service = new DeletedUsersAuditService(auditRepository, new AuditQueryMapper());
    }

    @Test
    void mapsStoredSnapshotAndPageMetadata() {
        LocalDateTime deleted = LocalDateTime.now();
        DeletedUserAuditRow row = new DeletedUserAuditRow("Jo Bloggs", "jo@example.test", "actor", deleted,
                "LeftFirm", "Left firm");
        when(auditRepository.findDeletedUsers(eq("%"), eq(PageRequest.of(1, 5))))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 5), 6));

        DeletedUserAuditPage page = service.getDeletedUsers(query(1, 5, null), internalActor(Permission.VIEW_EXTERNAL_USER));

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.userEntraObjectId()).isNull();
            assertThat(item.name()).isEqualTo("Jo Bloggs");
            assertThat(item.email()).isEqualTo("jo@example.test");
            assertThat(item.deletedBy()).isEqualTo("actor");
            assertThat(item.deletedDate()).isEqualTo(deleted);
            assertThat(item.deleteReason()).isEqualTo(DeleteUserReason.LeftFirm);
            assertThat(item.deleteReasonLabel()).isEqualTo("Left firm");
        });
        assertThat(page.page().pageNumber()).isEqualTo(1);
        assertThat(page.page().pageSize()).isEqualTo(5);
        assertThat(page.page().totalElements()).isEqualTo(6);
        assertThat(page.page().totalPages()).isEqualTo(2);
    }

    @Test
    void noMatchesReturnsEmptyItemsWithPageMetadata() {
        when(auditRepository.findDeletedUsers(any(), any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        DeletedUserAuditPage page = service.getDeletedUsers(query(null, null, "nobody"), internalActor(Permission.VIEW_EXTERNAL_USER));

        assertThat(page.items()).isEmpty();
        assertThat(page.page().pageSize()).isEqualTo(20);
        assertThat(page.page().totalElements()).isZero();
    }

    @Test
    void withoutViewExternalUserTheRepositoryIsNotQueriedAndAnEmptyPageIsReturned() {
        DeletedUserAuditPage page = service.getDeletedUsers(query(2, 10, null), internalActor());

        assertThat(page.items()).isEmpty();
        assertThat(page.page().pageNumber()).isEqualTo(2);
        assertThat(page.page().pageSize()).isEqualTo(10);
        assertThat(page.page().totalElements()).isZero();
        verifyNoInteractions(auditRepository);
    }

    @Test
    void emailQueryIsLowerCasedTrimmedAndLikeEscaped() {
        when(auditRepository.findDeletedUsers(any(), any())).thenReturn(Page.empty());

        service.getDeletedUsers(query(null, null, "  A_b%c\\D "), internalActor(Permission.VIEW_EXTERNAL_USER));

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(auditRepository).findDeletedUsers(pattern.capture(), any());
        assertThat(pattern.getValue()).isEqualTo("%a\\_b\\%c\\\\d%");
    }

    @Test
    void pageSizeIsCappedAtOneHundred() {
        when(auditRepository.findDeletedUsers(any(), any())).thenReturn(Page.empty());

        service.getDeletedUsers(query(0, 1000, null), internalActor(Permission.VIEW_EXTERNAL_USER));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findDeletedUsers(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    private static DeletedUsersAuditQuery query(Integer pageNumber, Integer pageSize, String emailQuery) {
        return new DeletedUsersAuditQuery(new AuditDeletedUsersCriteria(pageNumber, pageSize, emailQuery));
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
