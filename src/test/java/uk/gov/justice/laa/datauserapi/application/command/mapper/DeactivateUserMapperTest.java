package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DeactivateUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.request.DeactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.DeactivateUserReasonLookup;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.time.temporal.ChronoUnit;

@ExtendWith(MockitoExtension.class)
class DeactivateUserMapperTest {

    @Mock
    private DeactivateUserReasonRepository deactivateUserReasonRepository;

    private DeactivateUserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DeactivateUserMapper(deactivateUserReasonRepository);
    }

    @Nested
    @DisplayName("toAuditEntity tests")
    class ToAuditEntityTests {

        @Test
        @DisplayName("Should successfully map to UserAccountStatusAudit when reason exists in lookup repository")
        void toAuditEntity_WhenReasonFound_ReturnsPopulatedAuditEntity() {
            // Arrange
            String actorId = "admin-actor-456";
            DeactivateUserReason reasonEnum = DeactivateUserReason.Absence;

            EntraUser entraUser = EntraUser.builder()
                    .id(UUID.randomUUID())
                    .entraOid("oid-12345")
                    .email("test.user@justice.gov.uk")
                    .build();

            DeactivateUserCommand command = DeactivateUserCommand.builder()
                    .userEntraObjectId(entraUser.getId())
                    .deactivateReason(reasonEnum)
                    .build();

            DeactivateUserReasonLookup lookupEntity = DeactivateUserReasonLookup.builder()
                    .id(UUID.randomUUID())
                    .name(reasonEnum.name())
                    .description("User contract terminated")
                    .build();

            when(deactivateUserReasonRepository.findByName(reasonEnum.name()))
                    .thenReturn(Optional.of(lookupEntity));

            LocalDateTime beforeCall = LocalDateTime.now();

            // Act
            UserAccountStatusAudit audit = mapper.toAuditEntity(entraUser, command, actorId);

            // Assert
            assertThat(audit).isNotNull();
            assertThat(audit.getEntraUser()).isSameAs(entraUser);
            assertThat(audit.getUserAccountStatus()).isEqualTo(UserAccountStatus.DEACTIVATED);
            assertThat(audit.getStatusChangedBy()).isEqualTo(actorId);
            assertThat(audit.getDeactivateUserReasonLookup()).isSameAs(lookupEntity);
            assertThat(audit.getStatusChangedDate())
                    .isNotNull()
                    .isCloseTo(beforeCall, within(2, ChronoUnit.SECONDS));

            verify(deactivateUserReasonRepository).findByName(reasonEnum.name());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when reason lookup is missing in repository")
        void toAuditEntity_WhenReasonNotFound_ThrowsIllegalArgumentException() {
            // Arrange
            String actorId = "system";
            DeactivateUserReason reasonEnum = DeactivateUserReason.Absence;

            EntraUser entraUser = EntraUser.builder()
                    .id(UUID.randomUUID())
                    .entraOid("oid-999")
                    .build();

            DeactivateUserCommand command = DeactivateUserCommand.builder()
                    .userEntraObjectId(entraUser.getId())
                    .deactivateReason(reasonEnum)
                    .build();

            when(deactivateUserReasonRepository.findByName(reasonEnum.name()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> mapper.toAuditEntity(entraUser, command, actorId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Deactivate reason not found: " + reasonEnum.name());

            verify(deactivateUserReasonRepository).findByName(reasonEnum.name());
        }
    }

    @Nested
    @DisplayName("toCommandResult tests")
    class ToCommandResultTests {

        @Test
        @DisplayName("Should build success CommandResult with formatted message containing entraOid")
        void toCommandResult_WithValidEntraUser_ReturnsSuccessResult() {
            // Arrange
            String entraOid = "entra-oid-abc-123";
            EntraUser entraUser = EntraUser.builder()
                    .id(UUID.randomUUID())
                    .entraOid(entraOid)
                    .build();

            // Act
            CommandResult result = mapper.toCommandResult(entraUser);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.success()).isTrue();
            assertThat(result.message())
                    .isEqualTo(String.format("User account '%s' deactivated successfully", entraOid));

            verifyNoInteractions(deactivateUserReasonRepository);
        }
    }

    @Nested
    @DisplayName("toDeactivateUserCommand tests")
    class ToDeactivateUserCommandTests {

        @Test
        @DisplayName("Should successfully map request to command when reason string is valid enum name")
        void toDeactivateUserCommand_WithValidReasonString_ReturnsCommand() {
            // Arrange
            UUID targetUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            DeactivateUserReason expectedReason = DeactivateUserReason.Absence;

            DeactivateUserRequest request = DeactivateUserRequest.builder()
                    .userEntraObjectId(targetUserId)
                    .deactivateReason(expectedReason.name())
                    .build();

            // Act
            DeactivateUserCommand command = mapper.toDeactivateUserCommand(request);

            // Assert
            assertThat(command).isNotNull();
            assertThat(command.userEntraObjectId()).isEqualTo(targetUserId);
            assertThat(command.deactivateReason()).isEqualTo(expectedReason);

            verifyNoInteractions(deactivateUserReasonRepository);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when reason string is invalid or does not match enum")
        void toDeactivateUserCommand_WithInvalidReasonString_ThrowsIllegalArgumentException() {
            // Arrange
            UUID targetUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            String invalidReason = "INVALID_REASON_CODE";

            DeactivateUserRequest request = DeactivateUserRequest.builder()
                    .userEntraObjectId(targetUserId)
                    .deactivateReason(invalidReason)
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> mapper.toDeactivateUserCommand(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid deactivate reason: " + invalidReason);

            verifyNoInteractions(deactivateUserReasonRepository);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when reason string is null")
        void toDeactivateUserCommand_WithNullReasonString_ThrowsIllegalArgumentException() {
            // Arrange
            UUID targetUserId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

            DeactivateUserRequest request = DeactivateUserRequest.builder()
                    .userEntraObjectId(targetUserId)
                    .deactivateReason(null)
                    .build();

            // Act & Assert
            // DeactivateUserReason.valueOf(null) throws NullPointerException, caught as non-matching or bubbling
            assertThatThrownBy(() -> mapper.toDeactivateUserCommand(request))
                    .isInstanceOf(Exception.class);

            verifyNoInteractions(deactivateUserReasonRepository);
        }
    }
}
