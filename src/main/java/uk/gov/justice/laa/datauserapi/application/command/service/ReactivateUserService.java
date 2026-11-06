package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.datauserapi.application.command.policy.ReactivationHierarchyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;


/**
 * Permission ({@code ENABLE_EXTERNAL_USER}), self/internal-target rules and firm scoping are enforced declaratively on
 * {@link ReactivateUserCommand} via {@code @RequiresPermission} and its policies before the handler invokes this service.
 */
@Slf4j
@Service
public class ReactivateUserService {

    private final UserAccountLifecycleService lifecycleService;
    private final TechServicesClient techServicesClient;

    public ReactivateUserService(UserAccountLifecycleService lifecycleService, TechServicesClient techServicesClient) {
        this.lifecycleService = lifecycleService;
        this.techServicesClient = techServicesClient;
    }

    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult reactivate(ReactivateUserCommand command, AuthenticatedEntraUser actor) {
        log.info("Enabling user account",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()));

        CommandResult result = lifecycleService.reactivate(
                command.userEntraObjectId(), actor.entraOid(), command.comments(), target -> {
                    // Recheck under the row lock: the pre-handler policy may have raced with another deactivation.
                    if (!ReactivationHierarchyPolicy.canReactivate(target.deactivationType(), actor)) {
                        throw new AccessDeniedException(
                                "Actor roles cannot reactivate a user deactivated with type " + target.deactivationType());
                    }
                    techServicesClient.reactivateUser(target.user());
                });

        log.info("User account reactivated",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()));
        return result;
    }
}
