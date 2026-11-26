package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    @Mock
    private UserAccountQueryService queryService;

    @Spy
    private UserViewMapper userViewMapper = new UserViewMapper();

    private UserSearchService service;

    @BeforeEach
    void setUp() {
        service = new UserSearchService(queryService, userViewMapper);
    }

    @Test
    void shouldReturnPagedResultsForInternalActor() {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .userType(UserType.INTERNAL)
                .appRoles(Set.of())
                .build();
        
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actorOid")
                .activeProfile(profile)
                .build();

        EntraUserDto user = mock(EntraUserDto.class);
        UserAccountSummaryView summary = mock(UserAccountSummaryView.class);

        SearchUsersQuery query = new SearchUsersQuery(criteria());
        Page<EntraUserDto> page = new PageImpl<>(java.util.List.of(user));

        when(queryService.searchUsers(any(), any())).thenReturn(page);
        doReturn(summary).when(userViewMapper).mapToUserAccountSummaryView(user);

        UserAccountSummaryPage result = service.search(query, actor);

        assertThat(result.items()).containsExactly(summary);
        verify(queryService).searchUsers(any(), any());
    }

    @Test
    void shouldPassActorFirmIdForExternalUsers() {
        UUID firmId = UUID.randomUUID();
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .userType(UserType.EXTERNAL)
                .firmId(firmId)
                .appRoles(Set.of())
                .build();
        
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actorOid")
                .activeProfile(profile)
                .build();

        SearchUsersQuery query = new SearchUsersQuery(criteria());
        when(queryService.searchUsers(any(), any())).thenReturn(Page.empty());

        service.search(query, actor);

        ArgumentCaptor<UserSearchCriteria> captor = ArgumentCaptor.forClass(UserSearchCriteria.class);
        verify(queryService).searchUsers(captor.capture(), any());
        assertThat(captor.getValue().actorFirmId()).isEqualTo(firmId);
    }

    @Test
    void shouldSetCanViewExternalUsersWhenPermissionPresent() {
        AuthenticatedAppRole role = new AuthenticatedAppRole(
                UUID.randomUUID(), "role", 0, null, false,
                null, null, "desc", true, null, 
                Set.copyOf(Arrays.asList(Permission.VIEW_EXTERNAL_USER))
        );
        
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .userType(UserType.INTERNAL)
                .appRoles(Set.of(role))
                .build();
        
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actorOid")
                .activeProfile(profile)
                .build();

        SearchUsersQuery query = new SearchUsersQuery(criteria());
        when(queryService.searchUsers(any(), any())).thenReturn(Page.empty());

        service.search(query, actor);

        ArgumentCaptor<UserSearchCriteria> captor = ArgumentCaptor.forClass(UserSearchCriteria.class);
        verify(queryService).searchUsers(captor.capture(), any());
        assertThat(captor.getValue().canViewExternalUsers()).isTrue();
    }

    @Test
    void shouldUseDefaultPagingValues() {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .userType(UserType.INTERNAL)
                .appRoles(Set.of())
                .build();
        
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actorOid")
                .activeProfile(profile)
                .build();

        SearchUsersQuery query = new SearchUsersQuery(criteria());
        when(queryService.searchUsers(any(), any())).thenReturn(Page.empty());

        service.search(query, actor);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).searchUsers(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void shouldCapPageSizeAt100() {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .userType(UserType.INTERNAL)
                .appRoles(Set.of())
                .build();
        
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actorOid")
                .activeProfile(profile)
                .build();

        UserSearchCriteria criteria = new UserSearchCriteria(
                0, 500, null,
                null, null, null, null,
                null, null, null, null,
                null, false, false, null, null, false, false
        );

        SearchUsersQuery query = new SearchUsersQuery(criteria);
        when(queryService.searchUsers(any(), any())).thenReturn(Page.empty());

        service.search(query, actor);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).searchUsers(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    private UserSearchCriteria criteria() {
        return new UserSearchCriteria(
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                null, false, false, null, null, false, false
        );
    }

}
