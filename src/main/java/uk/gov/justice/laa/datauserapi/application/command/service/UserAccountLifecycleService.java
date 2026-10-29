package uk.gov.justice.laa.datauserapi.application.command.service;

import java.util.UUID;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

/**
 * Write-side owner of the user account status lifecycle. Handlers orchestrate and call this service so they never
 * touch persistence entities; every method joins the handler's transaction.
 */
@Service
public class UserAccountLifecycleService {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final DeactivateUserMapper deactivateUserMapper;
    private final ReactivateUserMapper reactivateUserMapper;
    private final ModelMapper modelMapper;

    public UserAccountLifecycleService(
            EntraUserCommandRepository entraUserCommandRepository,
            UserAccountStatusAuditRepository auditRepository,
            DeactivateUserMapper deactivateUserMapper,
            ReactivateUserMapper reactivateUserMapper,
            ModelMapper modelMapper) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.auditRepository = auditRepository;
        this.deactivateUserMapper = deactivateUserMapper;
        this.reactivateUserMapper = reactivateUserMapper;
        this.modelMapper = modelMapper;
    }

    /** Detached DTO used only as input for outbound calls made by the command being handled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public EntraUserDto loadEntraUserDtoByEntraOid(UUID userEntraObjectId) {
        return modelMapper.map(load(userEntraObjectId), EntraUserDto.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CommandResult deactivate(UUID userEntraObjectId, String actorEntraOid,
                                    DeactivationType deactivationType, DeactivateUserReason reason) {
        EntraUser user = load(userEntraObjectId);
        user.deactivate(actorEntraOid, deactivationType);
        entraUserCommandRepository.save(user);
        auditRepository.save(deactivateUserMapper.toAuditEntity(user, reason, actorEntraOid));
        return deactivateUserMapper.toCommandResult(user);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CommandResult reactivate(UUID userEntraObjectId, String actorEntraOid, String comments) {
        EntraUser user = load(userEntraObjectId);
        user.reactivate(actorEntraOid);
        entraUserCommandRepository.save(user);
        auditRepository.save(reactivateUserMapper.toAuditEntity(user, actorEntraOid, comments));
        return reactivateUserMapper.toCommandResult(user);
    }

    private EntraUser load(UUID userEntraObjectId) {
        return entraUserCommandRepository.findByEntraOid(String.valueOf(userEntraObjectId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found for oid: " + userEntraObjectId));
    }
}
