package uk.gov.justice.laa.datauserapi.config;

import com.azure.identity.ClientSecretCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.datauserapi.client.ts.AccessTokenProvider;
import uk.gov.justice.laa.datauserapi.client.ts.DoNothingTechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.LiveTechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesApi;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.config.jwt.DevJwtDecoderConfig;

import static org.assertj.core.api.Assertions.assertThat;

class TechServicesConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TechServicesClientConfig.class, TechServicesAuthConfig.class,
                    TechServicesHttpConfig.class, TechServicesSerializationConfig.class)
            .withBean(CacheManager.class, ConcurrentMapCacheManager::new)
            .withPropertyValues(
                    "spring.security.tech.services.credentials.scope=test-scope",
                    "spring.security.tech.services.credentials.client-id=test-client",
                    "spring.security.tech.services.credentials.client-secret=test-secret",
                    "spring.security.tech.services.credentials.tenant-id=test-tenant",
                    "spring.security.tech.services.credentials.base-url=http://localhost",
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://localhost/keys");

    @Test
    void composesLiveClientByDefaultWithOutboundCollaborators() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TechServicesClient.class);
            assertThat(context.getBean(TechServicesClient.class)).isInstanceOf(LiveTechServicesClient.class);
            assertThat(context).hasSingleBean(AccessTokenProvider.class).hasSingleBean(TechServicesApi.class);
            assertThat(context).hasSingleBean(ClientSecretCredential.class).hasSingleBean(RestClient.class);
            assertThat(context.getBean("tokenExpiryJwtDecoder")).isInstanceOf(JwtDecoder.class);
            assertThat(context.getBean(ClientHttpRequestFactory.class))
                    .isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
        });
    }

    @Test
    void selectsLiveClientWhenEnabled() {
        contextRunner.withPropertyValues("app.enable.tech.services.call=true").run(context ->
                assertThat(context.getBean(TechServicesClient.class)).isInstanceOf(LiveTechServicesClient.class));
    }

    @Test
    void selectsDoNothingClientWhenDisabled() {
        contextRunner.withPropertyValues("app.enable.tech.services.call=false").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TechServicesClient.class);
            assertThat(context.getBean(TechServicesClient.class)).isInstanceOf(DoNothingTechServicesClient.class);
        });
    }

    @Test
    void bindsExistingPropertyNamesAndDefaults() {
        contextRunner.run(context -> {
            TechServicesProperties credentials = context.getBean(TechServicesProperties.class);
            assertThat(credentials.getScope()).isEqualTo("test-scope");
            assertThat(credentials.getClientId()).isEqualTo("test-client");
            assertThat(credentials.getClientSecret()).isEqualTo("test-secret");
            assertThat(credentials.getTenantId()).isEqualTo("test-tenant");
            assertThat(credentials.getBaseUrl()).isEqualTo("http://localhost");
            HttpTimeoutProperties timeouts = context.getBean(HttpTimeoutProperties.class);
            assertThat(timeouts.read().timeout()).isEqualTo(30);
            assertThat(timeouts.connect().timeout()).isEqualTo(30);
            assertThat(context.getBean(TechServicesClientProperties.class).unit()).isEqualTo("laa");
        });
    }

    @Test
    void bindsCustomTimeoutsAndBusinessUnit() {
        contextRunner.withPropertyValues(
                "app.tech.services.req.read.timeout=45",
                "app.tech.services.req.connect.timeout=10",
                "app.tech.services.laa.business.unit=custom-unit").run(context -> {
            assertThat(context).hasNotFailed();
            HttpTimeoutProperties timeouts = context.getBean(HttpTimeoutProperties.class);
            assertThat(timeouts.read().timeout()).isEqualTo(45);
            assertThat(timeouts.connect().timeout()).isEqualTo(10);
            assertThat(context.getBean(TechServicesClientProperties.class).unit()).isEqualTo("custom-unit");
        });
    }

    @Test
    void preservesJavaTimeSerialization() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(mapper.writeValueAsString(Instant.parse("2026-01-01T00:00:00Z")))
                    .isEqualTo("\"2026-01-01T00:00:00Z\"");
        });
    }

    @Test
    void keepsInboundDecoderPrimaryAndOutboundDecoderQualified() {
        contextRunner.withUserConfiguration(DevJwtDecoderConfig.class)
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtDecoder inbound = context.getBean(JwtDecoder.class);
                    assertThat(inbound).isNotSameAs(context.getBean("tokenExpiryJwtDecoder"));
                    assertThat(inbound.decode("oid__test-oid_scope__user_data.read").getClaimAsString("oid"))
                            .isEqualTo("test-oid");
                });
    }
}
