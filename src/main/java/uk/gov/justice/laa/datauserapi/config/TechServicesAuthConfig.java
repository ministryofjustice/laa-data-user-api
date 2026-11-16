package uk.gov.justice.laa.datauserapi.config;

import java.time.Clock;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import uk.gov.justice.laa.datauserapi.config.jwt.JwtProperties;
import uk.gov.justice.laa.datauserapi.client.ts.AccessTokenProvider;
import uk.gov.justice.laa.datauserapi.client.ts.AzureTechServicesTokenProvider;

@Configuration
@EnableConfigurationProperties({TechServicesProperties.class, JwtProperties.class})
public class TechServicesAuthConfig {

    @Bean
    public ClientSecretCredential techServicesClientSecretCredential(TechServicesProperties properties) {
        return new ClientSecretCredentialBuilder()
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .tenantId(properties.getTenantId())
                .build();
    }

    @Bean("tokenExpiryJwtDecoder")
    public JwtDecoder jwtDecoderForTokenExpiry(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new JwtTimestampValidator());
        return decoder;
    }

    @Bean("techServicesClock")
    public Clock techServicesClock() {
        return Clock.systemUTC();
    }

    @Bean
    public AccessTokenProvider techServicesAccessTokenProvider(
            ClientSecretCredential credential, CacheManager cacheManager,
            @Qualifier("tokenExpiryJwtDecoder") JwtDecoder decoder,
            TechServicesProperties properties, @Qualifier("techServicesClock") Clock clock) {
        return new AzureTechServicesTokenProvider(credential, cacheManager, decoder, properties.getScope(), clock);
    }
}
