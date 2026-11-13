package uk.gov.justice.laa.datauserapi.client.ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.client.ts.request.ChangeAccountReactivationRequest;
import uk.gov.justice.laa.datauserapi.client.ts.request.UpdateSecurityGroupsRequest;
import uk.gov.justice.laa.datauserapi.client.ts.request.UpdateUserDetailsRequest;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorCode;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.MissingEntraOidException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@ExtendWith(MockitoExtension.class)
class LiveTechServicesClientTest {
    @Mock
    private AccessTokenProvider accessTokenProvider;
    @Mock
    private TechServicesApi techServicesApi;

    private LiveTechServicesClient client;
    private EntraUserDto user;

    @BeforeEach
    void setUp() {
        client = new LiveTechServicesClient(accessTokenProvider, techServicesApi,
                new TechServicesResultMapper(new ObjectMapper()), new EntraOidValidator(),
                "test-unit", "activation_code_email", "test-group");
        user = new EntraUserDto();
        user.setId("local-id");
        user.setEntraOid("user-123");
    }

    @Test
    void reactivationUsesCommonEndpointAndMapsSuccessfulResponse() {
        when(accessTokenProvider.getAccessToken()).thenReturn("test-token");
        when(techServicesApi.patch(eq("test-unit/users/user-123"), any(ChangeAccountReactivationRequest.class), eq("test-token")))
                .thenReturn(ResponseEntity.ok("{\"success\":true,\"message\":\"reactivated\"}"));

        TechServicesApiResponse<ChangeAccountReactivationResponse> response = client.reactivateUser(user);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getMessage()).isEqualTo("reactivated");
    }

    @Test
    void deactivationPreservesNullableReasonAndMapsHttpError() {
        when(accessTokenProvider.getAccessToken()).thenReturn("test-token");
        when(techServicesApi.patch(eq("test-unit/users/user-123"), any(ChangeAccountReactivationRequest.class), eq("test-token")))
                .thenReturn(ResponseEntity.status(HttpStatus.TOO_EARLY)
                        .body("{\"code\":\"TOO_EARLY\",\"message\":\"wait\"}"));

        TechServicesApiResponse<ChangeAccountReactivationResponse> response = client.deactivateUser(user, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getCode()).isEqualTo(new TechServicesErrorCode("TOO_EARLY"));
        verify(techServicesApi).patch(eq("test-unit/users/user-123"), any(ChangeAccountReactivationRequest.class), eq("test-token"));
    }

    @Test
    void deletesRoleAssignmentOnSuccessAndIgnoresNotFound() {
        when(accessTokenProvider.getAccessToken()).thenReturn("test-token");
        when(techServicesApi.patch(eq("test-unit/users/user-123"), any(UpdateSecurityGroupsRequest.class), eq("test-token")))
                .thenReturn(ResponseEntity.ok("{\"success\":true,\"groupsRemoved\":[]}"))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(""));

        assertThatCode(() -> client.deleteRoleAssignment(user)).doesNotThrowAnyException();
        assertThatCode(() -> client.deleteRoleAssignment(user)).doesNotThrowAnyException();
    }

    @Test
    void deleteRoleAssignmentRequiresSuccessBodyAndRejectsOtherErrors() {
        when(accessTokenProvider.getAccessToken()).thenReturn("test-token");
        when(techServicesApi.patch(any(), any(), any())).thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{}"));

        assertThatThrownBy(() -> client.deleteRoleAssignment(user))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessageContaining("400");
    }

    @Test
    void updatesUserDetailsAndMaps200Response() {
        when(accessTokenProvider.getAccessToken()).thenReturn("test-token");
        when(techServicesApi.patch(eq("test-unit/users/user-123"), any(UpdateUserDetailsRequest.class), eq("test-token")))
                .thenReturn(ResponseEntity.ok("{\"success\":true,\"message\":\"updated\"}"));

        TechServicesApiResponse<ChangeAccountEnabledResponse> response =
                client.updateUserDetails("user-123", "A", "B", "a@b.test");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getMessage()).isEqualTo("updated");
    }

    @Test
    void updateUserDetailsMapsHttpErrorAndUsesStatusFallbackWhenBodyIsEmpty() {
        when(accessTokenProvider.getAccessToken()).thenReturn("test-token");
        when(techServicesApi.patch(eq("test-unit/users/user-123"), any(UpdateUserDetailsRequest.class), eq("test-token")))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(""));

        TechServicesApiResponse<ChangeAccountEnabledResponse> response =
                client.updateUserDetails("user-123", "A", "B", "a@b.test");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getCode().value()).isEqualTo("404 NOT_FOUND");
        assertThat(response.getError().getMessage()).isEqualTo("Not Found");
    }

    @Test
    void updateUserDetailsRejectsUnexpectedHttpStatus() {
        when(accessTokenProvider.getAccessToken()).thenReturn("test-token");
        when(techServicesApi.patch(eq("test-unit/users/user-123"), any(UpdateUserDetailsRequest.class), eq("test-token")))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{\"success\":true}"));

        assertThatThrownBy(() -> client.updateUserDetails("user-123", "A", "B", "a@b.test"))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessageContaining("unexpected response status");
    }

    @Test
    void rejectsNullOrBlankOidWithoutCallingApi() {
        assertThatThrownBy(() -> client.updateUserDetails(null, "A", "B", "a@b.test"))
                .isInstanceOf(MissingEntraOidException.class);
        assertThatThrownBy(() -> client.reactivateUser(userWithOid("  ")))
                .isInstanceOf(MissingEntraOidException.class);
        assertThatThrownBy(() -> client.deactivateUser(null, null))
                .isInstanceOf(MissingEntraOidException.class);
        verifyNoInteractions(accessTokenProvider, techServicesApi);
    }

    @Test
    void commonPipelinePreservesActivationFlagAndDeactivationReason() {
        when(accessTokenProvider.getAccessToken()).thenReturn("test-token");
        when(techServicesApi.patch(any(), any(), any()))
                .thenReturn(ResponseEntity.ok("{\"success\":true}"));

        client.reactivateUser(user);
        client.deactivateUser(user, "USER_REQUEST");

        ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
        verify(techServicesApi, times(2)).patch(eq("test-unit/users/user-123"), requests.capture(), eq("test-token"));
        ObjectMapper objectMapper = new ObjectMapper();
        var activationBody = objectMapper.valueToTree(requests.getAllValues().get(0)).path("accountReactivationdBody");
        var deactivationBody = objectMapper.valueToTree(requests.getAllValues().get(1)).path("accountReactivationdBody");
        assertThat(activationBody.path("active").booleanValue()).isTrue();
        assertThat(activationBody.path("deactivationReason").isNull()).isTrue();
        assertThat(deactivationBody.path("active").booleanValue()).isFalse();
        assertThat(deactivationBody.path("deactivationReason").textValue()).isEqualTo("USER_REQUEST");
    }

    private EntraUserDto userWithOid(String oid) {
        EntraUserDto invalid = mock(EntraUserDto.class);
        when(invalid.getEntraOid()).thenReturn(oid);
        return invalid;
    }
}
