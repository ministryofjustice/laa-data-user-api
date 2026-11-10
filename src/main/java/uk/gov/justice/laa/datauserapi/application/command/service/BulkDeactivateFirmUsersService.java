package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult.Status;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult.UserOutcome;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.FirmNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@Slf4j
@Service
public class BulkDeactivateFirmUsersService {

    private final FirmCommandRepository firmRepository;
    private final EntraUserCommandRepository userRepository;
    private final DeactivateUserService deactivateUserService;

    public BulkDeactivateFirmUsersService(FirmCommandRepository firmRepository,
                                         EntraUserCommandRepository userRepository,
                                         DeactivateUserService deactivateUserService) {
        this.firmRepository = firmRepository;
        this.userRepository = userRepository;
        this.deactivateUserService = deactivateUserService;
    }

    public void requireFirmExists(String firmCode) {
        if (!firmRepository.existsByCode(firmCode)) {
            throw new FirmNotFoundException(firmCode);
        }
    }

    /**
     * Runs the bulk deactivation off the request thread. Failures are only observable through logs, so nothing
     * may escape: every per-user failure is recorded and a summary is logged at the end.
     */
    @Async("bulkDeactivationExecutor")
    public void deactivateAsync(BulkDeactivateFirmUsersCommand command, AuthenticatedEntraUser actor) {
        try {
            BulkDeactivationResult result = deactivate(command, actor);
            log.info("Bulk deactivation completed",
                    kv("firmCode", command.firmId()), kv("actorEntraOid", actor.entraOid()),
                    kv("deactivated", result.deactivated()), kv("failed", result.failed()));
        } catch (Exception ex) {
            log.error("Bulk deactivation aborted",
                    kv("firmCode", command.firmId()), kv("actorEntraOid", actor.entraOid()), ex);
        }
    }

    // Firm-level authorization is enforced by the bulk handler; each user gets an independent transaction.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BulkDeactivationResult deactivate(BulkDeactivateFirmUsersCommand command, AuthenticatedEntraUser actor) {
        requireFirmExists(command.firmId());
        List<UserOutcome> outcomes = new ArrayList<>();
        for (String entraOid : userRepository.findUserEntraOidsByFirmCode(command.firmId())) {
            UUID targetId;
            try {
                if (entraOid == null) {
                    throw new InvalidUuidFormatException("Missing user Entra object ID");
                }
                targetId = UUID.fromString(entraOid);
            } catch (InvalidUuidFormatException ex) {
                recordFailure(outcomes, entraOid, command.firmId(), Status.INVALID_USER_ID, ex);
                continue;
            } catch (IllegalArgumentException ex) {
                recordFailure(outcomes, entraOid, command.firmId(), Status.INVALID_USER_ID, ex);
                continue;
            }
            try {
                CommandResult result = deactivateUserService.deactivate(targetId, command.deactivateReason(), actor);
                Status status = result.success() ? Status.DEACTIVATED : Status.REJECTED;
                outcomes.add(new UserOutcome(entraOid, status));
                if (!result.success()) {
                    log.warn("Bulk deactivation rejected for user",
                            kv("entraOid", entraOid), kv("firmCode", command.firmId()),
                            kv("failureType", status), kv("reason", result.message()));
                }
            } catch (AccessDeniedException ex) {
                recordFailure(outcomes, entraOid, command.firmId(), Status.ACCESS_DENIED, ex);
            } catch (ResourceNotFoundException ex) {
                recordFailure(outcomes, entraOid, command.firmId(), Status.USER_NOT_FOUND, ex);
            } catch (TechServicesClientException ex) {
                recordFailure(outcomes, entraOid, command.firmId(), Status.DOWNSTREAM_FAILURE, ex);
            } catch (Exception ex) {
                // Preserve best-effort processing, but distinguish unexpected faults from business failures.
                recordFailure(outcomes, entraOid, command.firmId(), Status.SYSTEM_FAILURE, ex);
            }
        }
        return new BulkDeactivationResult(outcomes);
    }

    private void recordFailure(List<UserOutcome> outcomes, String entraOid, String firmCode, Status status,
                               Exception ex) {
        outcomes.add(new UserOutcome(entraOid, status));
        if (status == Status.SYSTEM_FAILURE) {
            log.error("Unexpected bulk deactivation failure",
                    kv("entraOid", entraOid), kv("firmCode", firmCode), kv("failureType", status), ex);
        } else {
            log.warn("Bulk deactivation failed for user",
                    kv("entraOid", entraOid), kv("firmCode", firmCode), kv("failureType", status), ex);
        }
    }
}
