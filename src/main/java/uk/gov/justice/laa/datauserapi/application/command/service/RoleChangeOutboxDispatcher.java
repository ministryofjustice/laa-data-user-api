package uk.gov.justice.laa.datauserapi.application.command.service;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.justice.laa.datauserapi.application.command.registry.SqsClientRegistry;
import uk.gov.justice.laa.datauserapi.exception.CcmsNotificationException;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;
import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
@Slf4j
public class RoleChangeOutboxDispatcher {
    private final RoleChangeOutboxState state;
    private final SqsClientRegistry registry;

    public RoleChangeOutboxDispatcher(RoleChangeOutboxState state, SqsClientRegistry registry) {
        this.state = state;
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${app.role-outbox.poll-delay-ms:1000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void dispatch() {
        for (var delivery : state.claim()) {
            boolean success = false;
            try {
                String url = registry.getSqsQueueUrl(delivery.applicationOid())
                        .filter(value -> !value.isBlank() && !"NONE".equalsIgnoreCase(value))
                        .orElseThrow(() -> new CcmsNotificationException(CcmsNotificationException.Reason.QUEUE_UNAVAILABLE,
                                "CCMS outbox queue is not configured", null));
                var client = registry.getSqsClient(delivery.applicationOid())
                        .orElseThrow(() -> new CcmsNotificationException(CcmsNotificationException.Reason.QUEUE_UNAVAILABLE,
                                "CCMS outbox client is not configured", null));
                client.sendMessage(SendMessageRequest.builder().queueUrl(url).messageBody(delivery.payload())
                        .messageGroupId(delivery.groupId()).messageDeduplicationId(delivery.eventId().toString())
                        .messageAttributes(Map.of(
                                "userType", MessageAttributeValue.builder().dataType("String").stringValue(delivery.userType()).build(),
                                "eventId", MessageAttributeValue.builder().dataType("String").stringValue(delivery.eventId().toString()).build(),
                                "eventSequence", MessageAttributeValue.builder().dataType("Number").stringValue(Long.toString(delivery.id())).build()))
                        .overrideConfiguration(configuration -> configuration.apiCallTimeout(Duration.ofSeconds(10)))
                        .build());
                success = true;
                log.info("Role outbox notification delivered", kv("event", "role_outbox_delivered"), kv("eventId", delivery.eventId()));
            } catch (RuntimeException exception) {
                // This background delivery boundary records durable retry state for each independent item.
                log.error("Role outbox notification failed; retry scheduled",
                        ExceptionLogFields.exceptionGroup(exception), ExceptionLogFields.exceptionReason(exception),
                        kv("event", "role_outbox_retry"), kv("eventId", delivery.eventId()), exception);
            }
            try {
                state.complete(delivery, success);
            } catch (RuntimeException exception) {
                log.error("Role outbox acknowledgement failed; lease expiry will permit retry",
                        ExceptionLogFields.exceptionGroup(exception), ExceptionLogFields.exceptionReason(exception),
                        kv("event", "role_outbox_acknowledgement_failed"), kv("eventId", delivery.eventId()), exception);
            }
        }
    }
}
