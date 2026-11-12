package uk.gov.justice.laa.datauserapi.application.command.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.core.exception.SdkClientException;
import uk.gov.justice.laa.datauserapi.exception.CcmsNotificationException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import uk.gov.justice.laa.datauserapi.application.command.registry.SqsClientRegistry;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleChangeNotificationServiceTest {

    private static final String APP = "app-oid";
    private static final String QUEUE = "https://sqs/queue";

    @Mock
    private SqsClientRegistry registry;
    @Mock
    private UserProfileCommandRepository repository;
    @Mock
    private SqsClient sqsClient;

    private RoleChangeNotificationService service;
    private UserProfile profile;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RoleChangeNotificationService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        service = new RoleChangeNotificationService(registry,
                new ObjectMapper().registerModule(new JavaTimeModule()), repository);
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
        SdkClientException failure = SdkClientException.create("down");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.sendMessage(profile, APP, Set.of(), Set.of("R")))
                .isSameAs(failure);
    }

    @Test
    void recoverReturnsFalse() {
        assertThat(service.recoverFromRetryFailure(
                new CcmsNotificationException("x", null), profile, APP, Set.of(), Set.of())).isFalse();
        assertThat(service.recoverFromRetryFailure(
                SdkClientException.create("x"), profile, APP, Set.of(), Set.of())).isFalse();
    }

    @Test
    void wrapsSerializationFailureWithItsCause() throws JsonProcessingException {
        ObjectMapper mapper = mock(ObjectMapper.class);
        JsonProcessingException failure = new JsonProcessingException("invalid JSON") {};
        when(mapper.writeValueAsString(any())).thenThrow(failure);
        RoleChangeNotificationService notification =
                new RoleChangeNotificationService(registry, mapper, repository);
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));

        assertThatThrownBy(() -> notification.sendMessage(profile, APP, Set.of("R"), Set.of()))
                .isInstanceOf(CcmsNotificationException.class).hasCause(failure);
        verifyNoInteractions(sqsClient);
        assertFailureEvents("CCMS notification attempt failed", "SERIALIZATION_FAILURE", 1);
        assertFailureEvents("All CCMS notification retries failed; continuing with database update",
                "SERIALIZATION_FAILURE", 0);
    }

    @Test
    void springProxyRetriesSdkFailuresThreeTimesThenReturnsFalse() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(SdkClientException.create("unavailable"));

        try (AnnotationConfigApplicationContext context = retryContext()) {
            assertThat(context.getBean(RoleChangeNotificationService.class)
                    .sendMessage(profile, APP, Set.of("R"), Set.of())).isFalse();
        }

        verify(sqsClient, times(3)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void springProxyRetriesSerializationFailuresThreeTimesThenReturnsFalse() throws JsonProcessingException {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("invalid JSON") {});
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));

        try (AnnotationConfigApplicationContext context = retryContext(mapper)) {
            assertThat(context.getBean(RoleChangeNotificationService.class)
                    .sendMessage(profile, APP, Set.of("R"), Set.of())).isFalse();
        }

        verify(mapper, times(3)).writeValueAsString(any());
        verifyNoInteractions(sqsClient);
        assertFailureEvents("CCMS notification attempt failed", "SERIALIZATION_FAILURE", 3);
        assertFailureEvents("All CCMS notification retries failed; continuing with database update",
                "SERIALIZATION_FAILURE", 1);
    }

    @Test
    void successfulRetryDoesNotLogHandledFailure() throws JsonProcessingException {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("temporary serialization failure") {})
                .thenReturn("{}");
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m").build());

        try (AnnotationConfigApplicationContext context = retryContext(mapper)) {
            assertThat(context.getBean(RoleChangeNotificationService.class)
                    .sendMessage(profile, APP, Set.of("R"), Set.of())).isTrue();
        }

        verify(mapper, times(2)).writeValueAsString(any());
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        assertFailureEvents("CCMS notification attempt failed", "SERIALIZATION_FAILURE", 1);
        assertFailureEvents("All CCMS notification retries failed; continuing with database update",
                "SERIALIZATION_FAILURE", 0);
    }

    @Test
    void springProxyDoesNotRetryOrRecoverUnexpectedProgrammingFaults() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        IllegalStateException failure = new IllegalStateException("unexpected bug");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(failure);

        try (AnnotationConfigApplicationContext context = retryContext()) {
            assertThatThrownBy(() -> context.getBean(RoleChangeNotificationService.class)
                    .sendMessage(profile, APP, Set.of("R"), Set.of())).isSameAs(failure);
        }

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    private AnnotationConfigApplicationContext retryContext() {
        return retryContext(new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private AnnotationConfigApplicationContext retryContext(ObjectMapper mapper) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(RetryConfiguration.class);
        context.registerBean(SqsClientRegistry.class, () -> registry);
        context.registerBean(ObjectMapper.class, () -> mapper);
        context.registerBean(UserProfileCommandRepository.class, () -> repository);
        context.register(RoleChangeNotificationService.class);
        context.refresh();
        return context;
    }

    @Configuration
    @EnableRetry(proxyTargetClass = true)
    static class RetryConfiguration {
    }

    @Test
    void roleSyncDoesNothingWithoutProfiles() {
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of());

        service.ccmsRoleSync();

        verify(repository, never()).save(any());
    }

    @Test
    void roleSyncMarksProfileSuccessfulAfterSending() {
        App app = App.builder().entraOid(APP).build();
        AppRole role = AppRole.builder().roleIdentifier("R").legacySync(true).app(app).build();
        profile.setAppRoles(new HashSet<>(Set.of(role)));
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(profile));
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m").build());

        service.ccmsRoleSync();

        assertThat(profile.isLastCcmsSyncSuccessful()).isTrue();
        verify(repository).save(profile);
    }

    @Test
    void roleSyncMarksProfileUnsuccessfulWhenSkipped() {
        App app = App.builder().entraOid(APP).build();
        AppRole role = AppRole.builder().roleIdentifier("R").legacySync(true).app(app).build();
        profile.setAppRoles(new HashSet<>(Set.of(role)));
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(profile));
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.empty());

        service.ccmsRoleSync();

        assertThat(profile.isLastCcmsSyncSuccessful()).isFalse();
        verify(repository).save(profile);
    }

    @Test
    void skipsWhenQueueBecomesNoneBetweenLookups() {
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE), Optional.of("NONE"));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));

        assertThat(service.sendMessage(profile, APP, Set.of(), Set.of("R"))).isFalse();
        verifyNoInteractions(sqsClient);
    }

    @Test
    void roleSyncWithMultipleAppsIsUnsuccessfulWhenAnyAppFails() {
        AppRole good = AppRole.builder().roleIdentifier("R1").legacySync(true).app(App.builder().entraOid(APP).build()).build();
        AppRole bad = AppRole.builder().roleIdentifier("R2").legacySync(true).app(App.builder().entraOid("other").build()).build();
        profile.setAppRoles(new HashSet<>(Set.of(good, bad)));
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(profile));
        when(registry.getSqsQueueUrl(APP)).thenReturn(Optional.of(QUEUE));
        when(registry.getSqsClient(APP)).thenReturn(Optional.of(sqsClient));
        when(registry.getSqsQueueUrl("other")).thenReturn(Optional.empty());
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m").build());

        service.ccmsRoleSync();

        assertThat(profile.isLastCcmsSyncSuccessful()).isFalse();
    }

    @Test
    void roleSyncContinuesAfterProfileError() {
        profile.setAppRoles(null);
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(profile));

        service.ccmsRoleSync();

        verify(repository, never()).save(any());
        assertFailureEvents("Failed to sync CCMS roles for profile", "INVALID_PROFILE", 1);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private void assertFailureEvents(String message, String reason, int expectedCount) {
        List<ILoggingEvent> events = appender.list.stream()
                .filter(event -> message.equals(event.getMessage()))
                .toList();
        assertThat(events).hasSize(expectedCount).allSatisfy(event -> {
            assertThat(event.getArgumentArray()).extracting(Object::toString)
                    .contains("exception_group=ccms_notification",
                            "exception_reason=" + reason.toLowerCase(java.util.Locale.ROOT));
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }
}
