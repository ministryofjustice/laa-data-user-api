package uk.gov.justice.laa.datauserapi.application.command.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UserAccountCreationMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserCreationRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileSilasStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserAccountPersistenceService {
    private final UserCreationRepository repository;
    private final UserAccountCreationMapper mapper;
    private final EntraUserCommandRepository entraUserRepository;
    private final UserProfileCommandRepository profileRepository;
    private final FirmCommandRepository firmRepository;

    public UserAccountPersistenceService(UserCreationRepository repository, UserAccountCreationMapper mapper,
                                         EntraUserCommandRepository entraUserRepository,
                                         UserProfileCommandRepository profileRepository,
                                         FirmCommandRepository firmRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.entraUserRepository = entraUserRepository;
        this.profileRepository = profileRepository;
        this.firmRepository = firmRepository;
    }

    @Transactional
    public CreateUserAccountResponse create(CreateUserAccountCommand command, UUID identityId, String actorOid) {
        repository.lockEmail(command.email());
        UUID firmId = UUID.fromString(command.firmId());
        var firm = firmRepository.findEnabledByIdForUpdate(firmId)
                .orElseThrow(() -> new CreateUserException(
                        CreateUserException.Reason.INVALID_REQUEST,
                        HttpStatus.BAD_REQUEST, "firmId does not identify an active firm", "firmId"));
        repository.findExisting(command.email()).map(mapper::toExistingResponse)
                .ifPresent(existing -> {
                    throw new UserAlreadyExistsException(existing);
                });
        repository.findExistingByEntraOid(identityId).map(mapper::toExistingResponse)
                .ifPresent(existing -> {
                    throw new UserAlreadyExistsException(existing);
                });
        EntraUser user = entraUserRepository.save(EntraUser.builder()
                .createdDate(LocalDateTime.now())
                .createdBy(actorOid)
                .email(command.email())
                .entraOid(identityId.toString())
                .firstName(command.firstName())
                .lastName(command.lastName())
                .userAccountStatus(UserAccountStatus.ACTIVATION_REQUIRED)
                .multiFirmUser(Boolean.TRUE.equals(command.multiFirmUser()))
                .active(true)
                .mailOnly(false)
                .ccmsEbsUser(false)
                .build());
        UserProfile profile = profileRepository.save(UserProfile.builder()
                .createdDate(LocalDateTime.now())
                .createdBy(actorOid)
                .activeProfile(true)
                .entraUser(user)
                .firm(firm)
                .legacyUserId(UUID.randomUUID())
                .userProfileStatus(UserProfileStatus.PENDING)
                .userType(UserType.EXTERNAL)
                .silasStatus(UserProfileSilasStatus.ACTIVATION_REQUIRED)
                .lastCcmsSyncSuccessful(false)
                .unrestrictedOfficeAccess(false)
                .build());
        UserAccountCreationData created = new UserAccountCreationData(identityId.toString(), profile.getId());
        return mapper.toCreatedResponse(created);
    }
}
