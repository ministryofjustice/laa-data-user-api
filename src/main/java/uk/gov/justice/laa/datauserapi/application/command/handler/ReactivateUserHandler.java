package uk.gov.justice.laa.datauserapi.application.command.handler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountLifecycleService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;


/**
 * Permission ({@code ENABLE_EXTERNAL_USER}), self/internal-target rules and firm scoping are enforced declaratively on
 * {@link ReactivateUserCommand} via {@code @RequiresPermission} and its policies before this handler runs.
 */
@Slf4j
@Component
public class ReactivateUserHandler implements CommandHandler<ReactivateUserCommand> {

    private final UserAccountLifecycleService lifecycleService;
    private final TechServicesClient techServicesClient;

    public ReactivateUserHandler(UserAccountLifecycleService lifecycleService, TechServicesClient techServicesClient) {
        this.lifecycleService = lifecycleService;
        this.techServicesClient = techServicesClient;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(ReactivateUserCommand command, AuthenticatedEntraUser actor) {
        log.info("Enabling user account",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()));

        techServicesClient.reactivateUser(lifecycleService.loadEntraUserDtoByEntraOid(command.userEntraObjectId()));
        CommandResult result = lifecycleService.reactivate(
                command.userEntraObjectId(), actor.entraOid(), command.comments());

        log.info("User account reactivated",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()));
        return result;
    }
}
