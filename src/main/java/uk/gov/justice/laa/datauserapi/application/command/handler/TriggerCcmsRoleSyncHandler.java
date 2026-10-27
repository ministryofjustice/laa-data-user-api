package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.ccms.TriggerCcmsRoleSyncCommand;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncService;
import uk.gov.justice.laa.datauserapi.contracts.response.TriggerCcmsRoleSyncResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@RequiredArgsConstructor
public class TriggerCcmsRoleSyncHandler implements CommandHandler<TriggerCcmsRoleSyncCommand, TriggerCcmsRoleSyncResult> {

    private final CcmsRoleSyncService syncService;

    @Override
    public TriggerCcmsRoleSyncResult handle(TriggerCcmsRoleSyncCommand command, AuthenticatedEntraUser actor) {
        return syncService.sync(actor.entraOid());
    }
}
