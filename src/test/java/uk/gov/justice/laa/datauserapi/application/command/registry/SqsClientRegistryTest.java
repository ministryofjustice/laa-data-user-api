package uk.gov.justice.laa.datauserapi.application.command.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import uk.gov.justice.laa.datauserapi.config.ccms.CcmsConfig;
import uk.gov.justice.laa.datauserapi.config.ccms.CcmsConnectionConfigProperties;
import uk.gov.justice.laa.datauserapi.config.ccms.UserConfig;


import static org.assertj.core.api.Assertions.assertThat;

class SqsClientRegistryTest {

    private SqsClientRegistry registry;

    @BeforeEach
    void setUp() {
        UserConfig.Sqs sqs = new UserConfig.Sqs();
        sqs.setArn("arn:aws:sqs:eu-west-2:123456789:queue");
        UserConfig.Api api = new UserConfig.Api();
        api.setSqs(sqs);
        UserConfig user = new UserConfig();
        user.setApi(api);
        CcmsConfig config = new CcmsConfig();
        config.setAppEntraObjectId("app-1");
        config.setUser(user);
        CcmsConnectionConfigProperties properties = new CcmsConnectionConfigProperties();
        properties.getConfigs().add(config);
        registry = new SqsClientRegistry(properties);
    }

    @Test
    void returnsEmptyForUnknownApp() {
        assertThat(registry.getSqsClient("unknown")).isEmpty();
        assertThat(registry.getSqsQueueUrl("unknown")).isEmpty();
    }

    @Test
    void returnsQueueUrlForKnownApp() {
        assertThat(registry.getSqsQueueUrl("app-1"))
                .contains("https://sqs.eu-west-2.amazonaws.com/123456789/queue");
    }

    @Test
    void cachesClientPerApp() {
        SqsClient first = registry.getSqsClient("app-1").orElseThrow();

        assertThat(registry.getSqsClient("app-1").orElseThrow()).isSameAs(first);
    }
}
