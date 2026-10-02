package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountSummaryView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.UserType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchUsersHandlerTest {

    @Mock
    private UserAccountQueryService queryService;

    @Mock
    private UserViewMapper userViewMapper;

    @InjectMocks
    private SearchUsersHandler handler;

    @Test
    void shouldReturnPagedResultsForInternalActor() {
        UserProfile profile = mock(UserProfile.class);

        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.INTERNAL);
        when(profile.getAppRoles()).thenReturn(Set.of());

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        EntraUserDto user = mock(EntraUserDto.class);

        UserAccountSummaryView summary = mock(UserAccountSummaryView.class);

        SearchUsersQuery query =
                new SearchUsersQuery( criteria(), "actorOid");

        Page<EntraUserDto> page =
                new PageImpl<>(List.of(user));

        when(queryService.findByEntraOid("actorOid"))
                .thenReturn(actor);

        when(queryService.searchUsers(any(), any()))
                .thenReturn(page);

        when(userViewMapper.mapToUserAccountSummaryView(user))
                .thenReturn(summary);

        UserAccountSummaryPage result = handler.handle(query);

        assertThat(result.items()).containsExactly(summary);

        verify(queryService).searchUsers(any(), any());
    }

    @Test
    void shouldThrowWhenActorHasNoActiveProfile() {
        UserProfile profile = mock(UserProfile.class);

        when(profile.isActiveProfile()).thenReturn(false);

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findByEntraOid("actorOid"))
                .thenReturn(actor);

        SearchUsersQuery query =
                new SearchUsersQuery( criteria(), "actorOid");

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(InvalidActorContextException.class);
    }

    @Test
    void shouldThrowWhenActorUserTypeCannotBeDetermined() {
        UserProfile profile = mock(UserProfile.class);

        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(null);

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findByEntraOid("actorOid"))
                .thenReturn(actor);

        SearchUsersQuery query =
                new SearchUsersQuery(mock(UserSearchCriteria.class), "actorOid");

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessage("Actor user type cannot be determined");
    }

    @Test
    void shouldPassActorFirmIdForExternalUsers() {
        Firm firm = mock(Firm.class);
        UUID firmId = UUID.randomUUID();

        when(firm.getId()).thenReturn(firmId);

        UserProfile profile = mock(UserProfile.class);
        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        when(profile.getFirm()).thenReturn(firm);
        when(profile.getAppRoles()).thenReturn(Set.of());

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findByEntraOid("actorOid"))
                .thenReturn(actor);

        when(queryService.searchUsers(any(), any()))
                .thenReturn(Page.empty());

        SearchUsersQuery query = new SearchUsersQuery( criteria(), "actorOid");

        handler.handle(query);

        ArgumentCaptor<UserSearchCriteria> captor =
                ArgumentCaptor.forClass(UserSearchCriteria.class);

        verify(queryService).searchUsers(captor.capture(), any());

        assertThat(captor.getValue().actorFirmId())
                .isEqualTo(firmId);
    }

    @Test
    void shouldSetCanViewExternalUsersWhenPermissionPresent() {
        AppRole role = mock(AppRole.class);

        when(role.getPermissions())
                .thenReturn(Set.of(Permission.VIEW_EXTERNAL_USER));

        UserProfile profile = mock(UserProfile.class);

        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.INTERNAL);
        when(profile.getAppRoles()).thenReturn(Set.of(role));

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findByEntraOid("actorOid"))
                .thenReturn(actor);

        when(queryService.searchUsers(any(), any()))
                .thenReturn(Page.empty());

        handler.handle(new SearchUsersQuery(criteria(), "actorOid"));

        ArgumentCaptor<UserSearchCriteria> captor =
                ArgumentCaptor.forClass(UserSearchCriteria.class);

        verify(queryService).searchUsers(captor.capture(), any());

        assertThat(captor.getValue().canViewExternalUsers())
                .isTrue();
    }

    @Test
    void shouldUseDefaultPagingValues() {
        UserProfile profile = internalProfile();

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findByEntraOid("actorOid"))
                .thenReturn(actor);

        when(queryService.searchUsers(any(), any()))
                .thenReturn(Page.empty());

        handler.handle(new SearchUsersQuery( criteria(), "actorOid"));

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(queryService)
                .searchUsers(any(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void shouldCapPageSizeAt100() {
        UserProfile profile = internalProfile();

        EntraUserDto actor = mock(EntraUserDto.class);
        when(actor.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findByEntraOid("actorOid"))
                .thenReturn(actor);

        when(queryService.searchUsers(any(), any()))
                .thenReturn(Page.empty());

        UserSearchCriteria criteria = new UserSearchCriteria(
                0, 500, null,
                null, null, null,
                null, null, null,
                null, null,
                false, false, null, false
        );

        handler.handle(new SearchUsersQuery( criteria, "actorOid"));

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(queryService)
                .searchUsers(any(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageSize())
                .isEqualTo(100);
    }

    private UserProfile internalProfile() {
        UserProfile profile = mock(UserProfile.class);

        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.INTERNAL);
        when(profile.getAppRoles()).thenReturn(Set.of());

        return profile;
    }

    private UserSearchCriteria criteria() {
        UserSearchCriteria criteria = new UserSearchCriteria(
                null, null, null,
                null, null, null,
                null, null, null,
                null, null,
                false, false, null, false
        );
        return criteria;
    }

}
