package uk.gov.justice.laa.datauserapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.tech.services.laa.business")
public record TechServicesClientProperties(@DefaultValue("laa") String unit) { }
