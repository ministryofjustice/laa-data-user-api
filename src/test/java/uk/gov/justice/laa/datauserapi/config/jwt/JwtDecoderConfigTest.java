package uk.gov.justice.laa.datauserapi.config.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtDecoderConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtDecoderConfig.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://localhost/issuer",
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://localhost/keys",
                    "spring.security.oauth2.resourceserver.jwt.audience=test-audience");

    @Test
    void createsProductionDecoder() {
        contextRunner.run(context ->
                assertThat(context).hasNotFailed().hasSingleBean(JwtDecoder.class));
    }

    @Test
    void propagatesDecoderConstructionFailure() {
        contextRunner.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }
}
