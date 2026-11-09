package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryprofile.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.GetActiveProfileQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveProfileHandlerTest {

    @Mock
    private GetActiveProfileQueryService getActiveProfileQueryService;

    @Mock
    private ModelMapper modelMapper;

    private String userEntraObjectId;

    @BeforeEach
    void setUp() {
        userEntraObjectId = UUID.randomUUID().toString();
    }

    @Test
    void delegatesToServiceAndMaps() {
        UserProfile profile = mock(UserProfile.class);
        UserProfileDetailView expected = mock(UserProfileDetailView.class);
        
        when(getActiveProfileQueryService.getUserActiveProfile(userEntraObjectId))
                .thenReturn(expected);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(getActiveProfileQueryService, modelMapper);
        GetActiveProfileQuery query = new GetActiveProfileQuery(userEntraObjectId);
        UserProfileDetailView result = handler.handle(query);

        assertThat(result).isNotNull();
        verify(getActiveProfileQueryService).getUserActiveProfile(userEntraObjectId);
    }
}
