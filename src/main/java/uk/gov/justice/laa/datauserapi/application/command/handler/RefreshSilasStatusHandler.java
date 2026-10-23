package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.service.SilasStatusRefreshService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;

@Slf4j
@Component
public class RefreshSilasStatusHandler implements CommandHandler<RefreshSilasStatusCommand> {

    private final SilasStatusRefreshService refreshService;

    public RefreshSilasStatusHandler(SilasStatusRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Override
    public CommandResult handle(RefreshSilasStatusCommand command, String actorOid) {
        try {
            refreshService.refresh(actorOid);
        } catch (TaskRejectedException ex) {
            log.warn("SiLAS status refresh requested by {} could not be started", actorOid, ex);
            return CommandResult.failure("SiLAS status refresh is already running or the worker is unavailable.");
        }
        return CommandResult.success("SiLAS status refresh accepted for asynchronous processing.");
    }
}
