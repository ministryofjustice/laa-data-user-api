package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.mapper.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveProfileQueryServiceTest {

    @Mock
    private UserProfileQueryRepository userProfileQueryRepository;

    private GetActiveProfileQueryMapper getActiveProfileQueryMapper;
    private GetActiveProfileQueryService service;
    private String userEntraObjectId;

    @BeforeEach
    void setUp() {
        userEntraObjectId = UUID.randomUUID().toString();
        getActiveProfileQueryMapper = mock(GetActiveProfileQueryMapper.class);
        service = new GetActiveProfileQueryService(userProfileQueryRepository, getActiveProfileQueryMapper);
    }

    @Test
    void returnsUserProfileWhenFound() {
        UserProfile profile = mock(UserProfile.class);

        UserProfileDetailView detailView = mock(UserProfileDetailView.class);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId)).thenReturn(Optional.of(profile));
        when(getActiveProfileQueryMapper.mapProfileToDetailView(profile)).thenReturn(detailView);

        UserProfileDetailView result = service.getUserActiveProfile(userEntraObjectId);

        assertThat(result).isSameAs(detailView);
        verify(userProfileQueryRepository).findActiveProfileWithDetailsAndRoles(userEntraObjectId);
        verify(getActiveProfileQueryMapper).mapProfileToDetailView(profile);
    }

    @Test
    void throwsNotFoundWhenProfileDoesNotExist() {
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserActiveProfile(userEntraObjectId))
                .isInstanceOf(UserProfileNotFoundException.class)
                .hasMessageContaining("Active profile not found");
    }
}
