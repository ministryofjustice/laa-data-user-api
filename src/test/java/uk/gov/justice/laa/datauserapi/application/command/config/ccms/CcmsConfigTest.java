package uk.gov.justice.laa.datauserapi.application.command.config.ccms;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CcmsConfigTest {

    private static final String ARN = "arn:aws:sqs:eu-west-2:123456789:my-queue.fifo";

    private UserConfig.Sqs sqs(String arn) {
        UserConfig.Sqs sqs = new UserConfig.Sqs();
        sqs.setArn(arn);
        return sqs;
    }

    private CcmsConfig config(String oid) {
        CcmsConfig config = new CcmsConfig();
        config.setAppEntraObjectId(oid);
        return config;
    }

    @Test
    void parsesQueueUrlAndRegion() {
        assertThat(sqs(ARN).parseSqsQueueUrlFromArn())
                .isEqualTo("https://sqs.eu-west-2.amazonaws.com/123456789/my-queue.fifo");
        assertThat(sqs(ARN).parseRegionFromArn()).isEqualTo("eu-west-2");
    }

    @Test
    void noneArnIsSkipped() {
        assertThat(sqs("NONE").parseSqsQueueUrlFromArn()).isEqualTo("none");
        assertThat(sqs("none").parseRegionFromArn()).isEqualTo("none");
    }

    @Test
    void invalidArnsAreRejected() {
        assertThatThrownBy(() -> sqs("bad").parseSqsQueueUrlFromArn()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sqs(null).parseSqsQueueUrlFromArn()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sqs("arn:aws:sqs:eu-west-2:1").parseSqsQueueUrlFromArn())
                .hasMessageContaining("6 parts");
        assertThatThrownBy(() -> sqs("bad").parseRegionFromArn()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sqs(null).parseRegionFromArn()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sqs("arn:aws:sqs:eu-west-2:1").parseRegionFromArn())
                .hasMessageContaining("6 parts");
    }

    @Test
    void isValidRejectsNullBlankAndNone() {
        assertThat(config("oid").isValid()).isTrue();
        assertThat(config(null).isValid()).isFalse();
        assertThat(config(" ").isValid()).isFalse();
        assertThat(config("none").isValid()).isFalse();
    }

    @Test
    void propertiesExposeOnlyActiveConfigsKeepingFirstDuplicate() {
        CcmsConnectionConfigProperties properties = new CcmsConnectionConfigProperties();
        CcmsConfig first = config("a");
        properties.getConfigs().addAll(java.util.List.of(first, config("a"), config("NONE")));

        Map<String, CcmsConfig> active = properties.getActiveConfigsByAppId();

        assertThat(active).containsOnlyKeys("a");
        assertThat(active.get("a")).isSameAs(first);
    }
}
