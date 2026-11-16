package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;


@Slf4j
@Service
public class DeactivateUserService {

    private final UserAccountLifecycleService lifecycleService;
    private final DeactivationTypeResolver deactivationTypeResolver;
    private final TechServicesClient techServicesClient;

    public DeactivateUserService(
            UserAccountLifecycleService lifecycleService,
            DeactivationTypeResolver deactivationTypeResolver,
            TechServicesClient techServicesClient) {
        this.lifecycleService = lifecycleService;
        this.deactivationTypeResolver = deactivationTypeResolver;
        this.techServicesClient = techServicesClient;
    }

    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult deactivate(UUID targetId, DeactivateUserReason reason, AuthenticatedEntraUser actor) {
        log.info("Deactivating user account",
                kv("entraOid", targetId),
                kv("actorEntraOid", actor.entraOid()));

        CommandResult result = lifecycleService.deactivate(
                targetId, actor.entraOid(), reason, user -> {
                    techServicesClient.deactivateUser(user, reason.name());
                    return deactivationTypeResolver.resolve(actor);
                });

        log.info("User account deactivated",
                kv("entraOid", targetId),
                kv("actorEntraOid", actor.entraOid()),
                kv("reason", reason));
        return result;
    }
}
