package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;
import uk.gov.justice.laa.datauserapi.application.command.dto.RoleNotificationDetails;
import uk.gov.justice.laa.datauserapi.application.command.model.CcmsMessage;
import uk.gov.justice.laa.datauserapi.application.command.registry.SqsClientRegistry;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsNotificationException;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleChangeNotificationService implements CcmsSyncSender {
    private static final String USER_TYPE_ATTRIBUTE = "userType";
    private static final DateTimeFormatter DEDUPLICATION_HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");

    private final SqsClientRegistry sqsClientRegistry;
    private final ObjectMapper objectMapper;

    @Retryable(
            retryFor = {CcmsNotificationException.class, SdkException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public boolean sendMessage(UserProfile userProfile, String appEntraOid, Set<String> newRoles, Set<String> oldRoles) {
        return sendMessage(details(userProfile), appEntraOid, newRoles, oldRoles);
    }

    @Retryable(
            retryFor = {CcmsNotificationException.class, SdkException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public boolean sendMessage(RoleNotificationDetails profile, String appEntraOid, Set<String> newRoles,
                               Set<String> oldRoles) {
        Optional<String> queue = sqsClientRegistry.getSqsQueueUrl(appEntraOid)
                .filter(url -> !url.isBlank() && !"NONE".equalsIgnoreCase(url));
        if (queue.isEmpty()) {
            log.info("Skipping CCMS update because the queue is unavailable",
                    kv("entraOid", profile.entraOid()), kv("applicationEntraOid", appEntraOid));
            return false;
        }

        try {
            var client = sqsClientRegistry.getSqsClient(appEntraOid);
            Optional<String> currentQueue = sqsClientRegistry.getSqsQueueUrl(appEntraOid)
                    .filter(url -> !url.isBlank() && !"NONE".equalsIgnoreCase(url));
            if (currentQueue.isEmpty()) {
                log.info("Skipping CCMS update because the queue is unavailable",
                        kv("entraOid", profile.entraOid()), kv("applicationEntraOid", appEntraOid));
                return false;
            }
            if (client.isEmpty()) {
                log.info("Skipping CCMS update because the queue client is unavailable",
                        kv("entraOid", profile.entraOid()), kv("applicationEntraOid", appEntraOid));
                return false;
            }
            if (newRoles.equals(oldRoles)) {
                log.info("No CCMS roles changed; skipping notification",
                        kv("userProfileId", profile.profileId()), kv("entraOid", profile.entraOid()),
                        kv("applicationEntraOid", appEntraOid));
                return true;
            }
            log.info("Generating CCMS role change message",
                    kv("userProfileId", profile.profileId()), kv("entraOid", profile.entraOid()),
                    kv("applicationEntraOid", appEntraOid));
            var message = roleChangeMessage(profile, newRoles);
            log.info("CCMS role change message generated", kv("userProfileId", profile.profileId()),
                    kv("entraOid", profile.entraOid()), kv("legacyUserId", profile.legacyUserId()),
                    kv("ccmsRoles", newRoles));
            var response = sendPreparedMessage(client.get(), currentQueue.get(), message, profile.userType(),
                    deduplicationId(profile, appEntraOid, newRoles, LocalDateTime.now()));
            log.info("CCMS role change message sent to queue", kv("userProfileId", profile.profileId()),
                    kv("entraOid", profile.entraOid()), kv("messageId", response.messageId()));
            return true;
        } catch (CcmsNotificationException | SdkException exception) {
            log.warn("CCMS notification attempt failed",
                    ExceptionLogFields.exceptionGroup(exception), ExceptionLogFields.exceptionReason(exception),
                    kv("entraOid", profile.entraOid()), kv("applicationEntraOid", appEntraOid),
                    kv("reason", exception instanceof CcmsNotificationException notification
                            ? notification.reason() : "SDK_FAILURE"),
                    exception);
            throw exception;
        }
    }

    @Override
    public void sendSyncMessage(CcmsSyncProfile profile, String appOid, Set<String> roles, LocalDateTime timestamp) {
        var client = sqsClientRegistry.getSqsClient(appOid);
        var queue = sqsClientRegistry.getSqsQueueUrl(appOid)
                .filter(url -> !url.isBlank() && !"NONE".equalsIgnoreCase(url));
        if (client.isEmpty() || queue.isEmpty()) {
            throw new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_UNAVAILABLE);
        }
        var message = CcmsMessage.builder().userName(profile.legacyUserId().toString())
                .firstName(profile.firstName()).lastName(profile.lastName()).email(profile.email())
                .vendorNumber(profile.vendorNumber()).timestamp(timestamp)
                .responsibilityKey(roles.stream().sorted().toList()).build();
        String key = profile.id() + "|" + appOid + "|" + String.join("|", roles.stream().sorted().toList())
                + "|" + timestamp.format(DEDUPLICATION_HOUR);
        try {
            sendPreparedMessage(client.get(), queue.get(), message, profile.userType(),
                    UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
        } catch (ApiCallTimeoutException | ApiCallAttemptTimeoutException exception) {
            throw new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT, exception);
        } catch (AwsServiceException exception) {
            CcmsSyncException.Reason reason = switch (exception.statusCode()) {
                case 408, 504 -> CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT;
                case 429, 503 -> CcmsSyncException.Reason.DOWNSTREAM_UNAVAILABLE;
                default -> CcmsSyncException.Reason.DOWNSTREAM_REJECTED;
            };
            throw new CcmsSyncException(reason, exception);
        } catch (SdkClientException exception) {
            for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof java.net.SocketTimeoutException
                        || cause instanceof java.net.http.HttpTimeoutException
                        || cause instanceof java.util.concurrent.TimeoutException) {
                    throw new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT, exception);
                }
            }
            throw new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_UNAVAILABLE, exception);
        } catch (CcmsNotificationException exception) {
            throw new CcmsSyncException(CcmsSyncException.Reason.MESSAGE_PREPARATION_FAILURE, exception);
        }
    }

    private CcmsMessage roleChangeMessage(RoleNotificationDetails profile, Set<String> roles) {
        var builder = CcmsMessage.builder().userName(profile.legacyUserId().toString())
                .firstName(profile.firstName()).lastName(profile.lastName()).timestamp(LocalDateTime.now())
                .email(profile.email()).responsibilityKey(roles.stream().sorted().toList());
        if (UserType.EXTERNAL.equals(profile.userType())) {
            builder.vendorNumber(profile.firmCode());
        }
        return builder.build();
    }

    private SendMessageResponse sendPreparedMessage(SqsClient client, String queue, CcmsMessage message,
                                                    UserType userType, String deduplicationId) {
        String body;
        try {
            body = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new CcmsNotificationException("CCMS message serialization failed", exception);
        }
        var request = SendMessageRequest.builder().queueUrl(queue).messageBody(body)
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
    public boolean propagateUnexpectedFailure(RuntimeException exception, UserProfile profile, String appOid,
                                              Set<String> newRoles, Set<String> oldRoles) {
        throw exception;
    }

    @Recover
    public boolean recoverFromRetryFailure(CcmsNotificationException exception, UserProfile profile, String appOid,
                                           Set<String> newRoles, Set<String> oldRoles) {
        return recordRetryFailure(exception, profile.getId(), profile.getEntraUser().getEntraOid(), appOid);
    }

    @Recover
    public boolean recoverFromRetryFailure(SdkException exception, UserProfile profile, String appOid,
                                           Set<String> newRoles, Set<String> oldRoles) {
        return recordRetryFailure(exception, profile.getId(), profile.getEntraUser().getEntraOid(), appOid);
    }

    @Recover
    public boolean recoverFromRetryFailure(CcmsNotificationException exception, RoleNotificationDetails profile,
                                           String appOid, Set<String> newRoles, Set<String> oldRoles) {
        return recordRetryFailure(exception, profile.profileId(), profile.entraOid(), appOid);
    }

    @Recover
    public boolean recoverFromRetryFailure(SdkException exception, RoleNotificationDetails profile, String appOid,
                                           Set<String> newRoles, Set<String> oldRoles) {
        return recordRetryFailure(exception, profile.profileId(), profile.entraOid(), appOid);
    }

    private boolean recordRetryFailure(RuntimeException exception, UUID profileId, String entraOid, String appOid) {
        log.error("All CCMS notification retries failed; continuing with database update",
                ExceptionLogFields.exceptionGroup(exception), ExceptionLogFields.exceptionReason(exception),
                kv("userProfileId", profileId), kv("entraOid", entraOid), kv("applicationEntraOid", appOid),
                kv("reason", exception instanceof CcmsNotificationException notification
                        ? notification.reason() : "SDK_FAILURE"), exception);
        return false;
    }

    private static String deduplicationId(RoleNotificationDetails profile, String appOid, Set<String> roles,
                                          LocalDateTime timestamp) {
        String key = profile.entraOid() + "|" + profile.legacyUserId() + "|" + appOid + "|"
                + String.join("|", roles.stream().sorted().toList()) + "|" + timestamp.format(DEDUPLICATION_HOUR);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static RoleNotificationDetails details(UserProfile profile) {
        EntraUser user = profile.getEntraUser();
        return new RoleNotificationDetails(profile.getId(), user.getEntraOid(), profile.getLegacyUserId(),
                profile.getUserType(), profile.getFirm() == null ? null : profile.getFirm().getCode(),
                user.getFirstName(), user.getLastName(), user.getEmail());
    }
}
