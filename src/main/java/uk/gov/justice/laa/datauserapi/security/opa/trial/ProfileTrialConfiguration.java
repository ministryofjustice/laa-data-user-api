package uk.gov.justice.laa.datauserapi.security.opa.trial;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProfileTrialConfiguration.Engines.class)
public class ProfileTrialConfiguration {
    @ConfigurationProperties("app.authorization")
    public record Engines(boolean legacyPoliciesEnabled, boolean opaEnabled) {
    }

    @Bean
    Clock profileTrialClock() {
        return Clock.systemUTC();
    }
}
