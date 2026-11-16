package uk.gov.justice.laa.datauserapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.tech.services.req")
public record HttpTimeoutProperties(@DefaultValue Read read, @DefaultValue Connect connect) {

    public record Read(@DefaultValue("30") int timeout) { }

    public record Connect(@DefaultValue("30") int timeout) { }
}
