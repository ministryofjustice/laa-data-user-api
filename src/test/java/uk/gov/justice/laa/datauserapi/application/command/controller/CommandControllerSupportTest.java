package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandControllerSupportTest {

    private final CommandControllerSupport underTest = new CommandControllerSupport() {};

    @Mock
    private Jwt jwt;

    @Test
    @DisplayName("Should return valid UUID string when JWT contains a valid oid claim")
    void resolveActor_shouldReturnOid_whenValidUuidPresent() {
        // Given
        String expectedOid = UUID.randomUUID().toString();
        when(jwt.getClaimAsString("oid")).thenReturn(expectedOid);

        // When
        String actualOid = underTest.resolveActor(jwt);

        // Then
        assertThat(actualOid).isEqualTo(expectedOid);
    }

    @Test
    @DisplayName("Should throw InvalidActorContextException when JWT is null")
    void resolveActor_shouldThrowException_whenJwtIsNull() {
        assertThatThrownBy(() -> underTest.resolveActor(null))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessage("Authentication token is missing the actor identity")
                .hasNoCause();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should throw InvalidActorContextException when oid claim is null, empty, or blank")
    void resolveActor_shouldThrowException_whenOidIsNullOrBlank(String invalidOid) {
        // Given
        when(jwt.getClaimAsString("oid")).thenReturn(invalidOid);

        // When / Then
        assertThatThrownBy(() -> underTest.resolveActor(jwt))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessage("Authentication token is missing the actor identity")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-valid-uuid",
        "12345",
        "11111111-2222-3333-4444-55555555555Z", // 36 chars, non-hex character 'Z'
        "c0ffee00-0000-0000-0000-00000000000000" // Malformed length
    })
    @DisplayName("Should throw InvalidActorContextException with cause when oid is not a valid UUID format")
    void resolveActor_shouldThrowException_whenOidIsNotValidUuid(String malformedOid) {
        // Given
        when(jwt.getClaimAsString("oid")).thenReturn(malformedOid);

        // When / Then
        assertThatThrownBy(() -> underTest.resolveActor(jwt))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessage("Authentication token contains an invalid actor identity")
                .hasCauseInstanceOf(Throwable.class);
    }
}
