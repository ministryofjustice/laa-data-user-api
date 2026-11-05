package uk.gov.justice.laa.datauserapi.config.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.security.oauth2.resourceserver.jwt")
public record JwtProperties(String issuerUri, String jwkSetUri, String audience) {
}
