package uk.gov.justice.laa.datauserapi.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties("spring.security.tech.services.credentials")
public class TechServicesProperties {

    private final String scope;
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String baseUrl;

    public TechServicesProperties(String scope, String clientId, String clientSecret, String tenantId, String baseUrl) {
        this.scope = scope;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
        this.baseUrl = baseUrl;
    }
}
