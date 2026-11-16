package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import uk.gov.justice.laa.datauserapi.application.command.model.CcmsMessage;
import uk.gov.justice.laa.datauserapi.application.command.registry.SqsClientRegistry;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;
import uk.gov.justice.laa.datauserapi.exception.CcmsNotificationException;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleChangeNotificationService implements CcmsSyncSender {

    private final SqsClientRegistry sqsClientRegistry;
    private final ObjectMapper objectMapper;

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
                    ExceptionLogFields.exceptionGroup(e), ExceptionLogFields.exceptionReason(e),
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("applicationEntraOid", appEntraOid),
                    kv("reason", e instanceof CcmsNotificationException notification
                            ? notification.reason() : "SDK_FAILURE"),
                    kv("exceptionMessage", e.getMessage()),
                    e);
            throw e;
        }
    }


    @Override
    public void sendSyncMessage(CcmsSyncProfile profile, String appOid, Set<String> roles, LocalDateTime timestamp) {
        var client = sqsClientRegistry.getSqsClient(appOid);
        var queue = sqsClientRegistry.getSqsQueueUrl(appOid).filter(url -> !"NONE".equalsIgnoreCase(url));
        if (client.isEmpty() || queue.isEmpty()) {
            throw new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_UNAVAILABLE);
        }
        var message = CcmsMessage.builder().userName(profile.legacyUserId().toString())
                .firstName(profile.firstName()).lastName(profile.lastName()).email(profile.email())
                .vendorNumber(profile.vendorNumber()).timestamp(timestamp)
                .responsibilityKey(roles.stream().sorted().toList()).build();
        String deduplicationKey = profile.id() + "|" + appOid + "|" + String.join("|", roles.stream().sorted().toList())
                + "|" + timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        String deduplicationId = UUID.nameUUIDFromBytes(deduplicationKey.getBytes(StandardCharsets.UTF_8)).toString();
        try {
            sendPreparedMessage(client.get(), queue.get(), message, profile.userType(), deduplicationId);
        } catch (ApiCallTimeoutException | ApiCallAttemptTimeoutException ex) {
            throw new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT, ex);
        } catch (AwsServiceException ex) {
            CcmsSyncException.Reason reason = switch (ex.statusCode()) {
                case 408, 504 -> CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT;
                case 429, 503 -> CcmsSyncException.Reason.DOWNSTREAM_UNAVAILABLE;
                default -> CcmsSyncException.Reason.DOWNSTREAM_REJECTED;
            };
            throw new CcmsSyncException(reason, ex);
        } catch (SdkClientException ex) {
            for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof java.net.SocketTimeoutException
                        || cause instanceof java.net.http.HttpTimeoutException
                        || cause instanceof java.util.concurrent.TimeoutException) {
                    throw new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT, ex);
                }
            }
            throw new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_UNAVAILABLE, ex);
        } catch (CcmsNotificationException ex) {
            throw new CcmsSyncException(CcmsSyncException.Reason.MESSAGE_PREPARATION_FAILURE, ex);
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

            log.info("CCMS role change message generated",
                    kv("userProfileId", userProfile.getId()),
                    kv("entraOid", userProfile.getEntraUser().getEntraOid()),
                    kv("legacyUserId", userProfile.getLegacyUserId()),
                    kv("ccmsRoles", newPuiRoles));

            SendMessageResponse response = sendPreparedMessage(sqsClient, sqsQueueUrl, ccmsMessageBuilder.build(),
                    userProfile.getUserType(), generateDeduplicationId(userProfile, newPuiRoles));
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

    private SendMessageResponse sendPreparedMessage(SqsClient client, String queue, CcmsMessage message,
                                                    UserType userType, String deduplicationId) {
        String messageBody;
        try {
            messageBody = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new CcmsNotificationException("CCMS message serialization failed", ex);
        }
        var request = SendMessageRequest.builder().queueUrl(queue).messageBody(messageBody)
                .messageAttributes(Map.of(USER_TYPE_ATTRIBUTE, MessageAttributeValue.builder()
                        .stringValue(userType.toString()).dataType("String").build()))
                .messageGroupId(message.getUserName()).messageDeduplicationId(deduplicationId).build();
        SendMessageResponse response = client.sendMessage(request);
        if (response == null || response.messageId() == null || response.messageId().isBlank()
                || (response.sdkHttpResponse() != null && !response.sdkHttpResponse().isSuccessful())) {
            throw new CcmsSyncException(CcmsSyncException.Reason.INVALID_RESPONSE);
        }
        return response;
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
                ExceptionLogFields.exceptionGroup(e), ExceptionLogFields.exceptionReason(e),
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
}
