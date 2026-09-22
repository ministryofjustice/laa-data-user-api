package uk.gov.justice.laa.datauserapi.application.command.mapper;

import jakarta.validation.Valid;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DisableUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DisableUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DisableUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.request.DisableUserRequest;
import uk.gov.justice.laa.datauserapi.entity.DisableUserReasonLookup;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class DisableUserMapper {

    private final DisableUserReasonRepository disableUserReasonRepository;

    public DisableUserMapper(DisableUserReasonRepository disableUserReasonRepository) {
        this.disableUserReasonRepository = disableUserReasonRepository;
    }

    public UserAccountStatusAudit toAuditEntity(EntraUser user, DisableUserCommand command, String actorId) {
        DisableUserReasonLookup disableUserReasonLookup = disableUserReasonRepository.findByName(command.disableReason().name())
                .orElseThrow(() -> new IllegalArgumentException("Disable reason not found: " + command.disableReason().name()));
        return UserAccountStatusAudit.builder()
                .entraUser(user)
                .userAccountStatus(UserAccountStatus.DEACTIVATED)
                .statusChangedBy(actorId)
                .statusChangedDate(LocalDateTime.now())
                .disableUserReasonLookup(disableUserReasonLookup)
                .build();
    }

    public CommandResult toCommandResult(EntraUser user) {
        return CommandResult.success(
                String.format("User account '%s' disabled successfully", user.getEntraOid())
        );
    }

    public DisableUserCommand toDisableUserCommand(@Valid DisableUserRequest disableUserRequest) {
        DisableUserReason disableReason = mapDisableReason(disableUserRequest.disableReason())
                .orElseThrow(() -> new IllegalArgumentException("Invalid disable reason: " + disableUserRequest.disableReason()));
        return DisableUserCommand.builder()
                .userEntraObjectId(disableUserRequest.userEntraObjectId())
                .disableReason(disableReason)
                .build();
    }

    private Optional<DisableUserReason> mapDisableReason(String disableUserReason) {
        try {
            return Optional.of(DisableUserReason.valueOf(disableUserReason));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
