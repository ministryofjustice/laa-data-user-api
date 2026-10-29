package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountLifecycleService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateUserHandlerTest {

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
    private DeactivationTypeResolver deactivationTypeResolver;
    @Mock
    private TechServicesClient techServicesClient;

    private DeactivateUserHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeactivateUserHandler(lifecycleService, deactivationTypeResolver, techServicesClient);
    }

    private DeactivateUserCommand command(DeactivateUserReason reason) {
        return DeactivateUserCommand.builder().userEntraObjectId(targetUserId).deactivateReason(reason).build();
    }

    @Test
    @DisplayName("Should propagate ResourceNotFoundException and call nothing downstream when the user is missing")
    void handle_WhenTargetUserNotFound_ThrowsException() {
        when(lifecycleService.loadEntraUserDtoByEntraOid(targetUserId))
                .thenThrow(new ResourceNotFoundException("User account not found for oid: " + targetUserId));

        assertThatThrownBy(() -> handler.handle(command(DeactivateUserReason.Absence), actor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User account not found for oid: " + targetUserId);

        verifyNoInteractions(techServicesClient, deactivationTypeResolver);
    }

    @Test
    @DisplayName("Should not change local state when the Tech Services call fails")
    void handle_WhenTechServicesClientFails_DoesNotDeactivateLocally() {
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).build();
        when(lifecycleService.loadEntraUserDtoByEntraOid(targetUserId)).thenReturn(userDto);
        doThrow(new TechServicesClientException("Downstream API timeout"))
                .when(techServicesClient).deactivateUser(userDto, DeactivateUserReason.NotActive.name());

        assertThatThrownBy(() -> handler.handle(command(DeactivateUserReason.NotActive), actor))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessage("Downstream API timeout");

        verify(lifecycleService, never()).deactivate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should call Tech Services, resolve the type from the actor, then deactivate locally")
    void handle_WhenSuccessful_DeactivatesAfterTechServices() {
        DeactivateUserCommand command = command(DeactivateUserReason.Absence);
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).build();
        CommandResult expected = CommandResult.success("User deactivated successfully");
        when(lifecycleService.loadEntraUserDtoByEntraOid(targetUserId)).thenReturn(userDto);
        when(deactivationTypeResolver.resolve(actor)).thenReturn(DeactivationType.LAA);
        when(lifecycleService.deactivate(
                targetUserId, actor.entraOid(), DeactivationType.LAA, DeactivateUserReason.Absence))
                .thenReturn(expected);

        CommandResult result = handler.handle(command, actor);

        assertThat(result).isSameAs(expected);
        InOrder inOrder = inOrder(lifecycleService, techServicesClient, deactivationTypeResolver);
        inOrder.verify(lifecycleService).loadEntraUserDtoByEntraOid(targetUserId);
        inOrder.verify(techServicesClient).deactivateUser(userDto, DeactivateUserReason.Absence.name());
        inOrder.verify(deactivationTypeResolver).resolve(actor);
        inOrder.verify(lifecycleService).deactivate(
                targetUserId, actor.entraOid(), DeactivationType.LAA, DeactivateUserReason.Absence);
    }
}
