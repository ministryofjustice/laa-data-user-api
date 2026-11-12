package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import uk.gov.justice.laa.datauserapi.application.command.model.CcmsMessage;
import uk.gov.justice.laa.datauserapi.application.command.registry.SqsClientRegistry;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsNotificationException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleChangeNotificationService {

    private final SqsClientRegistry sqsClientRegistry;
    private final ObjectMapper objectMapper;
    private final UserProfileCommandRepository userProfileRepository;

    private static final String USER_TYPE_ATTRIBUTE = "userType";

    /**
     * This method will automatically retry up to 3 times with 0.1 second delays
     *
     * @param userProfile The user profile
     * @param appEntraOid the app entra oid to determine which queue to send to
     * @param newPuiRoles new roles filtered by PUI
     * @param oldPuiRoles old roled filtered by PUI
     * @return true if successful
     */
    @Retryable(
            retryFor = {CcmsNotificationException.class, SdkException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public boolean sendMessage(UserProfile userProfile, String appEntraOid, Set<String> newPuiRoles, Set<String> oldPuiRoles) {
        Optional<String> sqsQueueUrlOpt = sqsClientRegistry.getSqsQueueUrl(appEntraOid);
        // Skip until queue is ready for env
        if (sqsQueueUrlOpt.isEmpty() || "NONE".equalsIgnoreCase(sqsQueueUrlOpt.get())) {
            log.info("Skipping CCMS update because the queue is unavailable",
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("applicationEntraOid", appEntraOid));
            return false;
        }

        try {
            log.info("Initializing CCMS role change message",
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("applicationEntraOid", appEntraOid));
            return sendRoleChangeNotificationToSqs(userProfile, appEntraOid, newPuiRoles, oldPuiRoles);
        } catch (CcmsNotificationException | SdkException e) {
            log.warn("CCMS notification attempt failed",
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("applicationEntraOid", appEntraOid),
                    kv("reason", e instanceof CcmsNotificationException notification
                            ? notification.reason() : "SDK_FAILURE"),
                    kv("exceptionMessage", e.getMessage()),
                    e);
            throw e;
        }
    }


    private boolean sendRoleChangeNotificationToSqs(UserProfile userProfile, String appEntraOid, Set<String> newPuiRoles, Set<String> oldPuiRoles) {
        Optional<SqsClient> sqsClientOpt = sqsClientRegistry.getSqsClient(appEntraOid);
        Optional<String> sqsQueueUrlOpt = sqsClientRegistry.getSqsQueueUrl(appEntraOid);
        if (sqsClientOpt.isEmpty() || sqsQueueUrlOpt.isEmpty() || "NONE".equalsIgnoreCase(sqsQueueUrlOpt.get())) {
            log.info("Skipping CCMS update because the queue or client is unavailable",
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("applicationEntraOid", appEntraOid));
            return false;
        }

        SqsClient sqsClient = sqsClientOpt.get();
        String sqsQueueUrl = sqsQueueUrlOpt.get();
        EntraUser entraUser = userProfile.getEntraUser();
        if (!newPuiRoles.equals(oldPuiRoles)) {
            log.info("Generating CCMS role change message",
                    kv("userProfileId", userProfile.getId()),
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("applicationEntraOid", appEntraOid));
            CcmsMessage.CcmsMessageBuilder ccmsMessageBuilder = CcmsMessage.builder()
                    .userName(userProfile.getLegacyUserId().toString())
                    .firstName(entraUser.getFirstName())
                    .lastName(entraUser.getLastName())
                    .timestamp(LocalDateTime.now())
                    .email(entraUser.getEmail())
                    .responsibilityKey(newPuiRoles.stream().toList());

            if (UserType.EXTERNAL.equals(userProfile.getUserType())) {
                ccmsMessageBuilder.vendorNumber(userProfile.getFirm().getCode());
            }

            CcmsMessage message = ccmsMessageBuilder.build();
            String messageBody;
            try {
                messageBody = objectMapper.writeValueAsString(message);
            } catch (JsonProcessingException ex) {
                throw new CcmsNotificationException("CCMS message serialization failed", ex);
            }
            log.info("CCMS role change message generated",
                    kv("userProfileId", userProfile.getId()),
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("legacyUserId", userProfile.getLegacyUserId()),
                    kv("ccmsRoles", newPuiRoles));

            Map<String, MessageAttributeValue> userTypeAttribute = Map.of(USER_TYPE_ATTRIBUTE, MessageAttributeValue.builder()
                    .stringValue(userProfile.getUserType().toString())
                    .dataType("String")
                    .build());

            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .messageBody(messageBody)
                    .messageAttributes(userTypeAttribute)
                    .messageGroupId(userProfile.getLegacyUserId().toString())
                    .messageDeduplicationId(generateDeduplicationId(userProfile, newPuiRoles))
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);
            log.info("CCMS role change message sent to queue",
                    kv("userProfileId", userProfile.getId()),
                    kv("entraOid", entraUser.getEntraOid()),
                    kv("messageId", response.messageId()));
        } else {
            log.info("No CCMS roles changed; skipping notification",
                    kv("userProfileId", userProfile.getId()),
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("applicationEntraOid", appEntraOid));
        }
        return true;
    }

    @Recover
    public boolean propagateUnexpectedFailure(RuntimeException e, UserProfile userProfile,
                                             String appEntraOid, Set<String> newPuiRoles, Set<String> oldPuiRoles) {
        throw e;
    }

    @Recover
    public boolean recoverFromRetryFailure(CcmsNotificationException e, UserProfile userProfile,
                                          String appEntraOid, Set<String> newPuiRoles, Set<String> oldPuiRoles) {
        return recordRetryFailure(e, userProfile, appEntraOid);
    }

    @Recover
    public boolean recoverFromRetryFailure(SdkException e, UserProfile userProfile,
                                          String appEntraOid, Set<String> newPuiRoles, Set<String> oldPuiRoles) {
        return recordRetryFailure(e, userProfile, appEntraOid);
    }

    private boolean recordRetryFailure(RuntimeException e, UserProfile userProfile, String appEntraOid) {
        log.error("All CCMS notification retries failed; continuing with database update",
                kv("userProfileId", userProfile.getId()),
                kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                kv("applicationEntraOid", appEntraOid),
                kv("reason", e instanceof CcmsNotificationException notification
                        ? notification.reason() : "SDK_FAILURE"),
                e);
        return false;
    }

    private String generateDeduplicationId(UserProfile userProfile, Set<String> newPuiRoles) {
        String content = userProfile.getEntraUser().getEntraOid() + "-"
                + userProfile.getLegacyUserId() + "-"
                + newPuiRoles.stream()
                .sorted()
                .reduce("", (a, b) -> a + b) + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));

        return UUID.nameUUIDFromBytes(content.getBytes()).toString();
    }


    @Async
    public void ccmsRoleSync() {
        List<UserProfile> profiles = userProfileRepository.findUserProfilesForCcmsSync();

        if (profiles.isEmpty()) {
            log.info("No profiles found for CCMS role sync",
                    kv("profileCount", 0));
            return;
        }

        AtomicInteger successful = new AtomicInteger();
        AtomicInteger unsuccessful = new AtomicInteger();

        profiles.forEach(profile -> {
            try {
                if (profile.getAppRoles() == null) {
                    throw new CcmsNotificationException(CcmsNotificationException.Reason.INVALID_PROFILE,
                            "CCMS profile roles are unavailable", null);
                }
                Map<String, Set<String>> ccmsRolesMap = profile.getAppRoles().stream()
                        .filter(AppRole::isLegacySync)
                        .collect(Collectors.groupingBy(role -> role.getApp().getEntraOid(),
                                Collectors.collectingAndThen(
                                        Collectors.mapping(AppRole::getRoleIdentifier, Collectors.toSet()),
                                        HashSet::new
                                )));

                boolean notificationSuccess = profile.isLastCcmsSyncSuccessful();
                boolean ccmsSyncResult = true;
                for (Map.Entry<String, Set<String>> ccmsRoles : ccmsRolesMap.entrySet()) {
                    boolean currentSyncResult = sendMessage(profile, ccmsRoles.getKey(), ccmsRoles.getValue(), Collections.emptySet());
                    ccmsSyncResult = ccmsSyncResult && currentSyncResult;
                    notificationSuccess = ccmsSyncResult;

                    log.info("CCMS role sync completed for profile",
                            kv("userProfileId", profile.getId()),
                            kv("entraOid", profile.getEntraUser().getEntraOid()),
                            kv("successful", notificationSuccess));
                }

                profile.setLastCcmsSyncSuccessful(notificationSuccess);
                userProfileRepository.save(profile);

                if (notificationSuccess) {
                    successful.incrementAndGet();
                } else {
                    unsuccessful.incrementAndGet();
                }
            } catch (CcmsNotificationException | SdkException | DataAccessException e) {
                log.error("Failed to sync CCMS roles for profile",
                        kv("userProfileId", profile.getId()),
                        kv("entraOid", profile.getEntraUser().getEntraOid()),
                        kv("failureType", e instanceof DataAccessException ? "DATABASE_FAILURE" : "NOTIFICATION_FAILURE"),
                        e);
                unsuccessful.incrementAndGet();
            } catch (RuntimeException e) {
                // Keep independent profile syncs running after an unexpected fault.
                log.error("Unexpected CCMS role sync failure",
                        kv("userProfileId", profile.getId()),
                        kv("failureType", "SYSTEM_FAILURE"),
                        e);
                unsuccessful.incrementAndGet();
            }

        });

        log.info("CCMS role sync completed",
                kv("successfulProfiles", successful.get()),
                kv("unsuccessfulProfiles", unsuccessful.get()));
    }

}
