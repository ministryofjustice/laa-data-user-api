package uk.gov.justice.laa.datauserapi.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CcmsSyncExceptionTest {

    @ParameterizedTest
    @MethodSource("reasonMessages")
    void exposesFixedMessageForTypedReason(CcmsSyncException.Reason reason, String message) {
        CcmsSyncException exception = new CcmsSyncException(reason);

        assertThat(exception).hasMessage(message).hasNoCause();
        assertThat(exception.reason()).isEqualTo(reason);
        assertThat(exception.groupName()).isEqualTo("ccms_sync");
    }

    private static Stream<Arguments> reasonMessages() {
        return Stream.of(
                Arguments.of(CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT, "CCMS SQS request timed out."),
                Arguments.of(CcmsSyncException.Reason.DOWNSTREAM_UNAVAILABLE,
                        "CCMS SQS queue or client is unavailable."),
                Arguments.of(CcmsSyncException.Reason.DOWNSTREAM_REJECTED,
                        "CCMS SQS rejected the synchronisation request."),
                Arguments.of(CcmsSyncException.Reason.INVALID_RESPONSE, "CCMS SQS returned an unexpected response."),
                Arguments.of(CcmsSyncException.Reason.MESSAGE_PREPARATION_FAILURE,
                        "CCMS synchronisation message could not be prepared."),
                Arguments.of(CcmsSyncException.Reason.INVALID_PROFILE, "CCMS profile is missing required data."),
                Arguments.of(CcmsSyncException.Reason.NO_SYNC_ROLES,
                        "No legacy-sync roles remain for the CCMS profile."));
    }

    @ParameterizedTest
    @EnumSource(CcmsSyncException.Reason.class)
    void preservesCauseWithoutReplacingSafeMessageOrClassification(CcmsSyncException.Reason reason) {
        Throwable cause = new IllegalStateException("downstream diagnostic detail");
        CcmsSyncException exception = new CcmsSyncException(reason, cause);

        assertThat(exception).hasCause(cause)
                .hasMessage(new CcmsSyncException(reason).getMessage());
        assertThat(exception.getMessage()).doesNotContain(cause.getMessage());
        assertThat(exception.reason()).isEqualTo(reason);
        assertThat(exception.groupName()).isEqualTo("ccms_sync");
    }

    @Test
    void rejectsNullReasonInBothConstructors() {
        assertThatThrownBy(() -> new CcmsSyncException(null))
                .isInstanceOf(NullPointerException.class).hasMessage("reason");
        assertThatThrownBy(() -> new CcmsSyncException(null, new IllegalStateException("cause")))
                .isInstanceOf(NullPointerException.class).hasMessage("reason");
    }

    @Test
    void storesOnlyTypedReasonWithoutHttpResponseFields() {
        assertThat(Arrays.stream(CcmsSyncException.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic()).map(Field::getName))
                .containsExactly("reason");
    }
}
