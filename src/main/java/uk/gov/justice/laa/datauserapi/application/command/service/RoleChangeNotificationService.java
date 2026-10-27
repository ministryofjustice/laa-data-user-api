package uk.gov.justice.laa.datauserapi.application.command.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
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

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleChangeNotificationService {

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
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public boolean sendMessage(UserProfile userProfile, String appEntraOid, Set<String> newPuiRoles, Set<String> oldPuiRoles) {
        Optional<String> sqsQueueUrlOpt = sqsClientRegistry.getSqsQueueUrl(appEntraOid);
        // Skip until queue is ready for env
        if (sqsQueueUrlOpt.isEmpty() || "NONE".equalsIgnoreCase(sqsQueueUrlOpt.get())) {
            log.info("Skipping CCMS update for user: {}",
                    userProfile.getEntraUser().getEntraOid());
            return false;
        }

        try {
            log.info("Initializing CCMS role change message for user: {}", userProfile.getEntraUser().getEntraOid());
            return sendRoleChangeNotificationToSqs(userProfile, appEntraOid, newPuiRoles, oldPuiRoles);
        } catch (Exception e) {
            log.warn("CCMS notification attempt failed for user: {}: {}",
                    userProfile.getEntraUser().getEntraOid(), e.getMessage());
            throw new RuntimeException("CCMS notification failed", e);
        }
    }


    public void sendSyncMessage(UserProfile profile, String appOid, Set<String> roles) {
        try {
            if (!sendRoleChangeNotificationToSqs(profile, appOid, roles, Set.of())) {
                throw new CcmsSyncException(HttpStatus.SERVICE_UNAVAILABLE, "CCMS SQS queue or client is unavailable.");
            }
        } catch (ApiCallTimeoutException | ApiCallAttemptTimeoutException ex) {
            throw new CcmsSyncException(HttpStatus.GATEWAY_TIMEOUT, "CCMS SQS request timed out.", ex);
        } catch (AwsServiceException ex) {
            HttpStatus status = switch (ex.statusCode()) {
                case 408, 504 -> HttpStatus.GATEWAY_TIMEOUT;
                case 429, 503 -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new CcmsSyncException(status, "CCMS SQS rejected the synchronization request.", ex);
        } catch (SdkClientException ex) {
            for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException
                        || cause instanceof HttpTimeoutException
                        || cause instanceof TimeoutException) {
                    throw new CcmsSyncException(HttpStatus.GATEWAY_TIMEOUT, "CCMS SQS request timed out.", ex);
                }
            }
            throw new CcmsSyncException(HttpStatus.SERVICE_UNAVAILABLE, "CCMS SQS queue is unreachable.", ex);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize CCMS synchronization message.", ex);
        }
    }

    private boolean sendRoleChangeNotificationToSqs(UserProfile userProfile, String appEntraOid, Set<String> newPuiRoles, Set<String> oldPuiRoles) throws JsonProcessingException {
        Optional<SqsClient> sqsClientOpt = sqsClientRegistry.getSqsClient(appEntraOid);
        Optional<String> sqsQueueUrlOpt = sqsClientRegistry.getSqsQueueUrl(appEntraOid);
        if (sqsClientOpt.isEmpty() || sqsQueueUrlOpt.isEmpty() || "NONE".equalsIgnoreCase(sqsQueueUrlOpt.get())) {
            log.info("Skipping CCMS update for user: {}", userProfile.getEntraUser().getEntraOid());
            return false;
        }

        SqsClient sqsClient = sqsClientOpt.get();
        String sqsQueueUrl = sqsQueueUrlOpt.get();
        EntraUser entraUser = userProfile.getEntraUser();
        if (!newPuiRoles.equals(oldPuiRoles)) {
            log.info("CCMS roles updated for user: {} with entra oid: {}, generating message", userProfile.getId(), userProfile.getEntraUser().getEntraOid());
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

            String messageBody = objectMapper.writeValueAsString(ccmsMessageBuilder.build());
            log.info("CCMS role change generated for user profile: {} with entra oid: {} and legacy profile id: {} containing CCMS roles: {}",
                    userProfile.getId(), userProfile.getEntraUser().getEntraOid(), userProfile.getLegacyUserId().toString(), newPuiRoles);

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
            if (response == null || response.messageId() == null || response.messageId().isBlank()
                    || (response.sdkHttpResponse() != null && !response.sdkHttpResponse().isSuccessful())) {
                throw new CcmsSyncException(HttpStatus.BAD_GATEWAY, "CCMS SQS returned an unexpected response.");
            }
            log.info("CCMS role change message sent to queue for user: {} with entra oid: {}, messageId: {}",
                    userProfile.getId(), entraUser.getEntraOid(), response.messageId());
        } else {
            log.info("No CCMS roles updated for user: {} with entra oid: {}, skipping", userProfile.getId(), userProfile.getEntraUser().getEntraOid());
        }
        return true;
    }

    @Recover
    public boolean recoverFromRetryFailure(RuntimeException e, UserProfile userProfile) {
        log.error("All retry attempts failed for CCMS notification for user: {} with entra oid: {}, saving roles to db and moving on",
                userProfile.getId(), userProfile.getEntraUser().getEntraOid(), e);
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
