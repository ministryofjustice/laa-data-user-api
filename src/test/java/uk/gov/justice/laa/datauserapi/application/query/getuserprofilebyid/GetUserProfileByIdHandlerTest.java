package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileReadRepository;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.util.TestObjectUtil;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class GetUserProfileByIdHandlerTest {

    @Mock
    private UserProfileReadRepository userProfileReadRepository;

    @Mock
    private GetUserProfileByIdReadMapper readMapper;

    private GetUserProfileByIdHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new GetUserProfileByIdHandler(userProfileReadRepository, readMapper);
    }

    @Test
    public void testHandleShouldReturnMappedReadModelWhenUserProfileExists() {
        // Given
        UUID id = UUID.randomUUID();
        GetUserProfileByIdQuery query = GetUserProfileByIdQuery.builder()
                .id(id)
                .build();
        UserProfile userProfile = TestObjectUtil.buildUserProfile();
        userProfile.setId(id);
        GetUserProfileByIdReadModel readModel = GetUserProfileByIdReadModel.builder().build();

        when(userProfileReadRepository.findById(query)).thenReturn(Optional.of(userProfile));
        when(readMapper.map(userProfile)).thenReturn(readModel);

        // When
        GetUserProfileByIdReadModel result = handler.handle(query);

        // Then
        Assertions.assertSame(readModel, result);
        verify(userProfileReadRepository, times(1)).findById(query);
        verify(readMapper, times(1)).map(userProfile);
    }

    @Test
    public void testHandleShouldThrowWhenUserProfileDoesNotExist() {
        // Given
        UUID id = UUID.randomUUID();
        GetUserProfileByIdQuery query = GetUserProfileByIdQuery.builder()
                .id(id)
                .build();

        when(userProfileReadRepository.findById(query)).thenReturn(Optional.empty());

        // When
        ResourceNotFoundException exception = Assertions.assertThrows(
                ResourceNotFoundException.class,
                () -> handler.handle(query)
        );

        // Then
        Assertions.assertEquals("Could not find user profile with id: " + id, exception.getMessage());
        verify(userProfileReadRepository, times(1)).findById(query);
        verifyNoInteractions(readMapper);
    }
}

