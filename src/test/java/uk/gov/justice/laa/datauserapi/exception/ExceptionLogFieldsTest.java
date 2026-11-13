package uk.gov.justice.laa.datauserapi.exception;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.argument.StructuredArgument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ExceptionLogFieldsTest {

    @Test
    void plainAuthorizationDenialsRetainTheirFallbackReason() {
        assertToken(new AuthorizationDeniedException("private diagnostic", new AuthorizationDecision(false)),
                "authorization_denied", "authorization_denied");
    }

    @Test
    void discoversNewLaaDataApiExceptionsWithoutLoggingChanges() {
        assertToken(new FutureBusinessException(), "stable_business_group", "new_business_failure");
    }

    private enum FutureReason {
        NEW_BUSINESS_FAILURE
    }

    private static class FutureBusinessException extends RuntimeException implements LaaDataApiException<FutureReason> {
        @Override
        public String groupName() {
            return "stable_business_group";
        }

        @Override
        public FutureReason reason() {
            return FutureReason.NEW_BUSINESS_FAILURE;
        }
    }

    @Test
    void explicitReasonConstructorsRejectNull() {
        assertThatNullPointerException().isThrownBy(() ->
                new CreateUserException(null, org.springframework.http.HttpStatus.BAD_REQUEST, "diagnostic"));
        assertThatNullPointerException().isThrownBy(() ->
                new CreateUserException(null, org.springframework.http.HttpStatus.BAD_REQUEST, "diagnostic", "field"));
        assertThatNullPointerException().isThrownBy(() ->
                new CreateUserException(null, org.springframework.http.HttpStatus.BAD_REQUEST, "diagnostic", new RuntimeException()));
        assertThatNullPointerException().isThrownBy(() ->
                new CreateUserException(CreateUserException.Reason.INVALID_REQUEST, null, "diagnostic"));
        assertThatNullPointerException().isThrownBy(() -> new InvalidRequestException(null, "diagnostic"));
        assertThatNullPointerException().isThrownBy(() -> new InvalidRequestException(null, "diagnostic", null));
        assertThatNullPointerException().isThrownBy(() -> new CcmsNotificationException(null, "diagnostic", null));
        assertThatNullPointerException().isThrownBy(() -> new TechServicesClientException(
                (TechServicesClientException.Reason) null, "diagnostic"));
        assertThatNullPointerException().isThrownBy(() -> new TechServicesClientException(null, "diagnostic", (Throwable) null));
    }

    @Test
    void legacyConstructorsRetainDefaultReasons() {
        assertThat(new CcmsNotificationException("diagnostic", null).reason())
                .isEqualTo(CcmsNotificationException.Reason.SERIALIZATION_FAILURE);
        TechServicesClientException[] failures = {
            new TechServicesClientException("diagnostic"),
            new TechServicesClientException("diagnostic", new IllegalStateException()),
            new TechServicesClientException("diagnostic %s", new String[]{"value"}),
            new TechServicesClientException("diagnostic", "code"),
            new TechServicesClientException("diagnostic", "code", new String[]{"detail"}),
            new TechServicesClientException(org.springframework.http.ResponseEntity.badRequest().build())
        };
        assertThat(failures).allSatisfy(failure -> {
            assertThat(failure.reason()).isEqualTo(TechServicesClientException.Reason.DOWNSTREAM_FAILURE);
            assertThat(failure.getReason()).isEqualTo(failure.reason());
        });
    }

    @ParameterizedTest
    @EnumSource(UserProfileNotFoundReason.class)
    void profileTokensDoNotDependOnIdentifiers(UserProfileNotFoundReason reason) {
        for (int i = 0; i < 2; i++) {
            UUID id = UUID.randomUUID();
            UserProfileNotFoundException failure = switch (reason) {
                case PROFILE_ID_NOT_FOUND -> new UserProfileNotFoundException(id);
                case ACTIVE_PROFILE_NOT_FOUND -> UserProfileNotFoundException.activeProfileForUser(id.toString());
            };
            assertToken(failure, "user_profile_not_found", reason.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    @ParameterizedTest
    @EnumSource(InvalidActorContextReason.class)
    void actorTokensUseTypedReasons(InvalidActorContextReason reason) {
        InvalidActorContextException failure = switch (reason) {
            case NO_ACTIVE_PROFILE -> InvalidActorContextException.noActiveProfile("private-actor");
            case UNDETERMINED_USER_TYPE -> InvalidActorContextException.undeterminedUserType();
            case MISSING_ACTOR_IDENTITY -> InvalidActorContextException.missingActorIdentity();
            case INVALID_ACTOR_IDENTITY -> InvalidActorContextException.invalidActorIdentity(
                    new IllegalArgumentException("private diagnostic"));
        };
        assertToken(failure, "invalid_actor_context", reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @ParameterizedTest
    @EnumSource(InvalidRequestException.Reason.class)
    void requestTokensDoNotDependOnMessages(InvalidRequestException.Reason reason) {
        assertToken(new InvalidRequestException(reason, "first diagnostic"),
                "invalid_request", reason.name().toLowerCase(java.util.Locale.ROOT));
        assertToken(new InvalidRequestException(reason, "changed diagnostic"),
                "invalid_request", reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @ParameterizedTest
    @EnumSource(CreateUserException.Reason.class)
    void creationTokensUseExplicitReasonsRatherThanHttpStatus(CreateUserException.Reason reason) {
        assertToken(new CreateUserException(reason, org.springframework.http.HttpStatus.BAD_GATEWAY, "private diagnostic"),
                "user_creation", reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @ParameterizedTest
    @EnumSource(TechServicesClientException.Reason.class)
    void techServicesTokensUseTypedReasons(TechServicesClientException.Reason reason) {
        assertToken(new TechServicesClientException(reason, "private diagnostic"),
                "tech_services_client", reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @ParameterizedTest
    @EnumSource(CcmsNotificationException.Reason.class)
    void ccmsTokensUseTypedReasons(CcmsNotificationException.Reason reason) {
        assertToken(new CcmsNotificationException(reason, "private diagnostic", null),
                "ccms_notification", reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void untypedFailuresUseExceptionKeyWithoutMessage() {
        assertToken(new UserNotFoundException("private-user"), "user_not_found", "user_not_found");
    }

    @Test
    void untypedProjectExceptionsDeclareStableGroups() {
        assertToken(new ResourceNotFoundException("private"), "resource_not_found", "resource_not_found");
        assertToken(new FirmNotFoundException("private"), "firm_not_found", "firm_not_found");
        assertToken(new ReferenceDataNotFoundException("private"), "reference_data_not_found", "reference_data_not_found");
        assertToken(new InvalidUuidFormatException("private"), "invalid_uuid_format", "invalid_uuid_format");
        assertToken(new JwtConfigurationException("private", null), "jwt_configuration", "jwt_configuration");
        assertToken(new ScopeMissingException("private"), "scope_missing", "scope_missing");
        UUID id = UUID.randomUUID();
        assertToken(new UserAlreadyDeactivatedException(id), "user_already_deactivated", "user_already_deactivated");
        assertToken(new UserNotDeactivatedException(id), "user_not_deactivated", "user_not_deactivated");
        assertToken(new UserAlreadyMultiFirmException("private"), "user_already_multi_firm", "user_already_multi_firm");
        assertToken(new uk.gov.justice.laa.datauserapi.security.rbac.InvalidAuthorizationConfigurationException("private"),
                "invalid_authorization_configuration", "invalid_authorization_configuration");
        assertToken(new MissingOidException(), "missing_oid", "missing_oid");
        assertToken(new MissingEntraOidException("private"), "missing_entra_oid", "missing_entra_oid");
    }

    @Test
    void extractedTechServicesExceptionsRetainTypedReasonsAndExplicitGroups() {
        assertToken(new TechServicesResponseMappingException("private"),
                "tech_services_response_mapping", "invalid_response");
        assertToken(new TechServicesResponseMappingException("private", new IllegalArgumentException()),
                "tech_services_response_mapping", "invalid_response");
        assertToken(new TechServicesTokenException("private"), "tech_services_token", "authentication_failure");
        assertToken(new TechServicesTokenException("private", new IllegalStateException()),
                "tech_services_token", "authentication_failure");
        assertToken(new TechServicesTransportException("private", new IllegalStateException()),
                "tech_services_transport", "downstream_failure");
    }

    @Test
    void untypedGroupContractOverridesClassNameForBothFields() {
        assertToken(new RenamedBusinessException(), "stable_group", "stable_group");
    }

    private static class RenamedBusinessException extends RuntimeException implements ExceptionGroup {
        @Override
        public String groupName() {
            return "stable_group";
        }
    }

    @Test
    void anonymousLibraryExceptionsUseNamedSuperclass() {
        assertToken(new com.fasterxml.jackson.core.JsonProcessingException("private") {},
                "json_processing", "json_processing");
    }

    @Test
    void groupsHandleAcronymsAndGenericExceptionNames() {
        assertThat(ExceptionLogFields.exceptionGroup(
                new org.springframework.security.oauth2.jwt.JwtException("private")).toString())
                .isEqualTo("exception_group=jwt");
        assertThat(ExceptionLogFields.exceptionGroup(new Exception("private")).toString())
                .isEqualTo("exception_group=exception");
        assertThat(ExceptionLogFields.exceptionGroup(new IllegalStateException("private")).toString())
                .isEqualTo("exception_group=illegal_state");
    }

    @Test
    void serializesTokenAsSearchableJsonField() throws IOException {
        UserProfileNotFoundException failure = UserProfileNotFoundException.activeProfileForUser("private-user");
        StructuredArgument field = ExceptionLogFields.exceptionReason(
                failure);
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = new JsonFactory().createGenerator(output)) {
            generator.writeStartObject();
            ExceptionLogFields.exceptionGroup(failure).writeTo(generator);
            field.writeTo(generator);
            generator.writeEndObject();
        }

        assertThat(output.toString()).isEqualTo(
                "{\"exception_group\":\"user_profile_not_found\","
                        + "\"exception_reason\":\"active_profile_not_found\"}");
    }

    private void assertToken(Throwable exception, String group, String expected) {
        assertThat(ExceptionLogFields.exceptionGroup(exception).toString())
                .isEqualTo("exception_group=" + group);
        assertThat(ExceptionLogFields.exceptionReason(exception).toString()).isEqualTo("exception_reason=" + expected);
    }
}
