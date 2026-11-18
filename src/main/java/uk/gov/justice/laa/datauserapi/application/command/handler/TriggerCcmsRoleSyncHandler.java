package uk.gov.justice.laa.datauserapi.application.command.handler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.ccms.TriggerCcmsRoleSyncCommand;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncService;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;

@Component
@Slf4j
@RequiredArgsConstructor
public class TriggerCcmsRoleSyncHandler implements CommandHandler<TriggerCcmsRoleSyncCommand, CommandResult> {

    private final CcmsRoleSyncService syncService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public CommandResult handle(TriggerCcmsRoleSyncCommand command, AuthenticatedEntraUser actor) {
        try {
            syncService.sync(actor.entraOid());
        } catch (TaskRejectedException ex) {
            log.warn("CCMS role sync could not be started",
                    ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                    kv("actorEntraOid", actor.entraOid()),
                    ex);
            return CommandResult.failure("CCMS role sync is already running or the worker is unavailable.");
        }
        return CommandResult.success("CCMS role sync accepted for asynchronous processing.");
    }
}
