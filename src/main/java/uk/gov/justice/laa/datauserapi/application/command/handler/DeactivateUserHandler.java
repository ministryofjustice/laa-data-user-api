package uk.gov.justice.laa.datauserapi.application.command.handler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountLifecycleService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;


/**
 * Permission ({@code DISABLE_EXTERNAL_USER}), self/internal-target rules and firm scoping are enforced declaratively on
 * {@link DeactivateUserCommand} via {@code @RequiresPermission} and its policies before this handler runs.
 */
@Slf4j
@Component
public class DeactivateUserHandler implements CommandHandler<DeactivateUserCommand> {

    private final UserAccountLifecycleService lifecycleService;
    private final DeactivationTypeResolver deactivationTypeResolver;
    private final TechServicesClient techServicesClient;

    public DeactivateUserHandler(
            UserAccountLifecycleService lifecycleService,
            DeactivationTypeResolver deactivationTypeResolver,
            TechServicesClient techServicesClient) {
        this.lifecycleService = lifecycleService;
        this.deactivationTypeResolver = deactivationTypeResolver;
        this.techServicesClient = techServicesClient;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(DeactivateUserCommand command, AuthenticatedEntraUser actor) {
        log.info("Handling deactivate user command",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()));

        techServicesClient.deactivateUser(
                lifecycleService.loadEntraUserDtoByEntraOid(command.userEntraObjectId()), command.deactivateReason().name());

        DeactivationType deactivationType = deactivationTypeResolver.resolve(actor);
        CommandResult result = lifecycleService.deactivate(
                command.userEntraObjectId(), actor.entraOid(), deactivationType, command.deactivateReason());

        log.info("User account deactivated",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()),
                kv("reason", command.deactivateReason()));
        return result;
    }
}
