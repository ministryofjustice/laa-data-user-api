package uk.gov.justice.laa.datauserapi.exception;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.argument.StructuredArgument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.StringWriter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionLogFieldsTest {

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
