package uk.gov.justice.laa.datauserapi.application.command.service;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.AuditableEntity;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.SilasAccountStatus;
import uk.gov.justice.laa.datauserapi.model.UserProfileSilasStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SilasStatusRefreshBatchService {

    private final EntraUserCommandRepository userRepository;
    private final UserProfileCommandRepository profileRepository;
    private final EntityManager entityManager;
    private final int batchSize;

    public SilasStatusRefreshBatchService(EntraUserCommandRepository userRepository,
                                         UserProfileCommandRepository profileRepository,
                                         EntityManager entityManager,
                                         @Value("${app.silas-status-refresh.batch-size:500}") int batchSize) {
        Assert.isTrue(batchSize > 0, "SiLAS status refresh batch size must be greater than zero");
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.entityManager = entityManager;
        this.batchSize = batchSize;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    public BatchResult refreshBatch(UUID afterId, String actorOid) {
        PageRequest page = PageRequest.of(0, batchSize);
        List<UUID> userIds = afterId == null
                ? userRepository.findRefreshUserIds(page)
                : userRepository.findRefreshUserIdsAfter(afterId, page);
        if (userIds.isEmpty()) {
            return new BatchResult(null, 0, 0);
        }

        entityManager.unwrap(Session.class).setJdbcBatchSize(50);
        List<EntraUser> users = userRepository.findRefreshUsersForUpdate(userIds);
        List<UserProfile> profiles = profileRepository.findRefreshProfilesForUpdate(userIds);
        // Fetch each collection separately, without paginating collection joins or creating a Cartesian product.
        profileRepository.fetchRefreshProfileRoles(userIds);
        profileRepository.fetchRefreshProfileOffices(userIds);

        Set<UUID> internalUserIds = profiles.stream()
                .filter(profile -> profile.getUserType() == UserType.INTERNAL)
                .map(profile -> profile.getEntraUser().getId())
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        int updatedAccounts = 0;
        int updatedProfiles = 0;
        for (EntraUser user : users) {
            SilasAccountStatus status = calculateAccountStatus(user, internalUserIds.contains(user.getId()));
            if (user.getSilasAccountStatus() != status) {
                user.setSilasAccountStatus(status);
                markModified(user, actorOid, now);
                updatedAccounts++;
            }
        }
        for (UserProfile profile : profiles) {
            UserProfileSilasStatus status = calculateProfileStatus(profile);
            if (profile.getSilasStatus() != status) {
                profile.setSilasStatus(status);
                markModified(profile, actorOid, now);
                updatedProfiles++;
            }
        }
        return new BatchResult(userIds.getLast(), updatedAccounts, updatedProfiles);
    }

    private SilasAccountStatus calculateAccountStatus(EntraUser user, boolean internal) {
        if (!internal && user.getInvitationStatus() != InvitationStatus.VERIFICATION_SUCCESS) {
            return SilasAccountStatus.ACTIVATION_REQUIRED;
        }
        return user.isActive() ? SilasAccountStatus.ACTIVE : SilasAccountStatus.DEACTIVATED;
    }

    private UserProfileSilasStatus calculateProfileStatus(UserProfile profile) {
        boolean internal = profile.getUserType() == UserType.INTERNAL;
        if (!internal && profile.getEntraUser().getInvitationStatus() != InvitationStatus.VERIFICATION_SUCCESS) {
            return UserProfileSilasStatus.ACTIVATION_REQUIRED;
        }
        if (profile.getAppRoles().isEmpty()
                || (!internal && !profile.isUnrestrictedOfficeAccess() && profile.getOffices().isEmpty())) {
            return UserProfileSilasStatus.NO_ACCESS_ASSIGNED;
        }
        return UserProfileSilasStatus.COMPLETE;
    }

    private void markModified(AuditableEntity entity, String actorOid, LocalDateTime now) {
        entity.setLastModifiedBy(actorOid);
        entity.setLastModified(now);
    }

    public record BatchResult(UUID lastUserId, int updatedAccounts, int updatedProfiles) {
    }
}
