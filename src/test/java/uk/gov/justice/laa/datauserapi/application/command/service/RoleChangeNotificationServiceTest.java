package uk.gov.justice.laa.datauserapi.application.command.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;
import uk.gov.justice.laa.datauserapi.application.command.registry.SqsClientRegistry;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleChangeNotificationServiceTest {

    private static final String APP = "app-oid";
    private static final String QUEUE = "https://sqs/queue";

    @Mock
    private SqsClientRegistry registry;
    @Mock
    private SqsClient sqsClient;

    private RoleChangeNotificationService service;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        service = new RoleChangeNotificationService(registry,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        EntraUser user = EntraUser.builder().entraOid("oid").firstName("A").lastName("B").email("a@b.com").build();
        profile = UserProfile.builder().entraUser(user).legacyUserId(UUID.randomUUID())
                .userType(UserType.EXTERNAL).firm(Firm.builder().code("F1").build()).build();
    }

    @Test
    void skipsWhenNoQueueConfigured() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.empty());

        assertThat(service.sendMessage(profile, APP, Set.of(), Set.of("R"))).isFalse();
        verifyNoInteractions(sqsClient);
    }

    @Test
    void skipsWhenQueueIsNone() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of("none"));

        assertThat(service.sendMessage(profile, APP, Set.of(), Set.of("R"))).isFalse();
    }

    @Test
    void sendsMessageWithVendorNumberForExternalUser() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m").build());

        assertThat(service.sendMessage(profile, APP, Collections.emptySet(), Set.of("R"))).isTrue();

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();
        assertThat(request.queueUrl()).isEqualTo(QUEUE);
        assertThat(request.messageBody()).contains("\"vendorNumber\":\"F1\"");
        assertThat(request.messageGroupId()).isEqualTo(profile.getLegacyUserId().toString());
        assertThat(request.messageAttributes()).containsKey("userType");
    }

    @Test
    void omitsVendorNumberForInternalUser() {
        profile.setUserType(UserType.INTERNAL);
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m").build());

        service.sendMessage(profile, APP, Set.of("N"), Set.of());

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        assertThat(captor.getValue().messageBody()).doesNotContain("vendorNumber");
    }

    @Test
    void doesNotSendWhenRolesUnchanged() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));

        assertThat(service.sendMessage(profile, APP, Set.of("R"), Set.of("R"))).isTrue();
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void doesNotSendWhenClientMissing() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.empty());

        assertThat(service.sendMessage(profile, APP, Set.of(), Set.of("R"))).isFalse();
    }

    @Test
    void wrapsSendFailures() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service.sendMessage(profile, APP, Set.of(), Set.of("R")))
                .isInstanceOf(RuntimeException.class).hasMessage("CCMS notification failed");
    }

    @Test
    void recoverReturnsFalse() {
        assertThat(service.recoverFromRetryFailure(new RuntimeException("x"), profile)).isFalse();
    }

    @Test
    void skipsWhenQueueBecomesNoneBetweenLookups() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE), Optional.of("NONE"));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));

        assertThat(service.sendMessage(profile, APP, Set.of(), Set.of("R"))).isFalse();
        verifyNoInteractions(sqsClient);
    }

    @ParameterizedTest
    @MethodSource("downstreamFailures")
    void syncMessageClassifiesDownstreamFailure(RuntimeException failure, HttpStatus status) {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.sendSyncMessage(profile, APP, Set.of("R")))
                .isInstanceOfSatisfying(CcmsSyncException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }

    private static Stream<Arguments> downstreamFailures() {
        return Stream.of(
                Arguments.of(ApiCallTimeoutException.create(1000), HttpStatus.GATEWAY_TIMEOUT),
                Arguments.of(ApiCallAttemptTimeoutException.create(1000), HttpStatus.GATEWAY_TIMEOUT),
                Arguments.of(SdkClientException.create("unreachable"), HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(SdkClientException.create("read timed out", new SocketTimeoutException()),
                        HttpStatus.GATEWAY_TIMEOUT),
                Arguments.of(SqsException.builder().statusCode(503).message("Unavailable").build(), HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(SqsException.builder().statusCode(429).message("Throttled").build(), HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(SqsException.builder().statusCode(504).message("Timeout").build(), HttpStatus.GATEWAY_TIMEOUT),
                Arguments.of(SqsException.builder().statusCode(500).message("Unexpected").build(), HttpStatus.BAD_GATEWAY));
    }

    @Test
    void syncMessageRejectsUnexpectedResponse() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        assertThatThrownBy(() -> service.sendSyncMessage(profile, APP, Set.of("R")))
                .isInstanceOfSatisfying(CcmsSyncException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void syncMessageReportsMissingClientAsUnavailable() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendSyncMessage(profile, APP, Set.of("R")))
                .isInstanceOfSatisfying(CcmsSyncException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

}
