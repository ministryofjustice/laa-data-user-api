package uk.gov.justice.laa.datauserapi.security.opa.trial;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.exception.OpaUnavailableException;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProfileTrialAuthorizerTest {
    private final JavaProfileTrialRules javaRules = mock(JavaProfileTrialRules.class);
    private final OpaAuthorizationClient client = mock(OpaAuthorizationClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ProfileTrialInput input = new ProfileTrialInput(ProfileTrialOperation.SEARCH,
            new ProfileTrialInput.Actor(UUID.randomUUID(), UUID.randomUUID(), "actor", UserType.INTERNAL,
                    null, Set.of(), Set.of(), Set.of(), true), null, Map.of(), Set.of(), Set.of(), true);
    private final ProfileTrialDecision allow = new ProfileTrialDecision(true, ProfileTrialDecision.Reason.ALLOWED,
            new ProfileTrialDecision.Filters(null, Set.of(UserType.INTERNAL, UserType.EXTERNAL), Set.of()));
    private final ProfileTrialDecision deny = ProfileTrialDecision.deny(ProfileTrialDecision.Reason.MISSING_PERMISSION);

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void engineFlagsChooseEnforcementWithoutDroppingTheJavaComparator(boolean legacyEnabled, boolean opaEnabled) {
        when(javaRules.evaluate(input)).thenReturn(deny);
        when(client.profileDecision(input)).thenReturn(allow);
        var authorizer = new ProfileTrialAuthorizer(javaRules, client,
                new ProfileTrialConfiguration.Engines(legacyEnabled, opaEnabled), clock);
        assertThat(authorizer.decide(input).allow()).isEqualTo(opaEnabled && !legacyEnabled);
        verify(javaRules).evaluate(input);
        if (opaEnabled) {
            verify(client).profileDecision(input);
        } else {
            verifyNoInteractions(client);
        }
    }

    @Test
    void opaDenialAndOutageNeverFallBackToAnAllowingJavaDecision() {
        when(javaRules.evaluate(input)).thenReturn(allow);
        when(client.profileDecision(input)).thenReturn(deny);
        var authorizer = new ProfileTrialAuthorizer(javaRules, client, new ProfileTrialConfiguration.Engines(true, true), clock);
        assertThat(authorizer.decide(input)).isEqualTo(deny);
        when(client.profileDecision(input)).thenThrow(new OpaUnavailableException("Unavailable"));
        assertThatThrownBy(() -> authorizer.decide(input)).isInstanceOf(OpaUnavailableException.class);
    }

    @Test
    void bothEnginesConstrainRatherThanUnionTheirQueryFilters() {
        var narrower = new ProfileTrialDecision(true, ProfileTrialDecision.Reason.ALLOWED,
                new ProfileTrialDecision.Filters(null, Set.of(UserType.EXTERNAL), Set.of()));
        when(javaRules.evaluate(input)).thenReturn(narrower);
        when(client.profileDecision(input)).thenReturn(allow);
        var authorizer = new ProfileTrialAuthorizer(javaRules, client, new ProfileTrialConfiguration.Engines(true, true), clock);
        assertThat(authorizer.decide(input)).isEqualTo(narrower);
    }
}
