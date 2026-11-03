package uk.gov.justice.laa.datauserapi.application.command.handler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.datauserapi.application.command.policy.ReactivationHierarchyPolicy;
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

        // Recheck under the row lock: the pre-handler policy may have raced with another deactivation.
        UserAccountLifecycleService.LockedUserForReactivation target =
                lifecycleService.loadUserForReactivation(command.userEntraObjectId());
        if (!ReactivationHierarchyPolicy.canReactivate(target.deactivationType(), actor)) {
            throw new AccessDeniedException(
                    "Actor roles cannot reactivate a user deactivated with type " + target.deactivationType());
        }

        techServicesClient.reactivateUser(target.user());
        CommandResult result = lifecycleService.reactivate(
                command.userEntraObjectId(), actor.entraOid(), command.comments());

        log.info("User account reactivated",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()));
        return result;
    }
}
