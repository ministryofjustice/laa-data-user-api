package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountLifecycleService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactivateUserHandlerTest {

    private final UUID targetUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
            .id(actorId)
            .entraOid(actorId.toString())
            .email("actor@example.com")
            .active(true)
            .build();

    @Mock
    private UserAccountLifecycleService lifecycleService;
    @Mock
    private TechServicesClient techServicesClient;

    private ReactivateUserHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReactivateUserHandler(lifecycleService, techServicesClient);
    }

    @Test
    @DisplayName("Should propagate ResourceNotFoundException and call nothing downstream when the user is missing")
    void handle_WhenTargetUserNotFound_ThrowsException() {
        ReactivateUserCommand command = ReactivateUserCommand.builder()
                .userEntraObjectId(targetUserId).comments("missing").build();
        when(lifecycleService.loadEntraUserDtoByEntraOid(targetUserId))
                .thenThrow(new ResourceNotFoundException("User account not found for oid: " + targetUserId));

        assertThatThrownBy(() -> handler.handle(command, actor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User account not found for oid: " + targetUserId);

        verifyNoInteractions(techServicesClient);
        verify(lifecycleService, never()).reactivate(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should not change local state when the Tech Services call fails")
    void handle_WhenTechServicesClientFails_DoesNotReactivateLocally() {
        ReactivateUserCommand command = ReactivateUserCommand.builder()
                .userEntraObjectId(targetUserId).comments("Reactivating user").build();
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).build();
        when(lifecycleService.loadEntraUserDtoByEntraOid(targetUserId)).thenReturn(userDto);
        doThrow(new TechServicesClientException("Downstream TS error"))
                .when(techServicesClient).reactivateUser(userDto);

        assertThatThrownBy(() -> handler.handle(command, actor))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessage("Downstream TS error");

        verify(lifecycleService, never()).reactivate(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should call Tech Services before reactivating locally and return the service result")
    void handle_WhenSuccessful_ReactivatesAfterTechServices() {
        String comments = "Account reactivation verified";
        ReactivateUserCommand command = ReactivateUserCommand.builder()
                .userEntraObjectId(targetUserId).comments(comments).build();
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).build();
        CommandResult expected = CommandResult.success("User reactivated successfully");
        when(lifecycleService.loadEntraUserDtoByEntraOid(targetUserId)).thenReturn(userDto);
        when(lifecycleService.reactivate(targetUserId, actor.entraOid(), comments)).thenReturn(expected);

        CommandResult result = handler.handle(command, actor);

        assertThat(result).isSameAs(expected);
        InOrder inOrder = inOrder(lifecycleService, techServicesClient);
        inOrder.verify(lifecycleService).loadEntraUserDtoByEntraOid(targetUserId);
        inOrder.verify(techServicesClient).reactivateUser(userDto);
        inOrder.verify(lifecycleService).reactivate(targetUserId, actor.entraOid(), comments);
    }
}
