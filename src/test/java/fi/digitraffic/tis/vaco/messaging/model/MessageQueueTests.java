package fi.digitraffic.tis.vaco.messaging.model;

import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.rules.validation.netex.EnturNetexValidatorRule;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

class MessageQueueTests {

    @Test
    void queueNamesMatchDesiredPattern() {
        for (MessageQueue mq : MessageQueue.values()) {
            String queueName = mq.getQueueName();
            // this test is only for non-patternet queue names, see test below
            if (!queueName.contains("{")) {
                assertThat(queueName, matchesPattern("^([a-z]+?-?)+$"));
            }
        }
    }

    @Test
    void ruleQueueMatchesDesiredPattern() {
        String versionedPattern = "^([a-z]+?-?)+v\\d+_\\d+_\\d$";
        String gtfsQueue = MessageQueue.RULES.munge(CanonicalGtfsValidatorRule.RULE_NAME);

        assertThat(gtfsQueue, equalTo("vaco-rules-gtfs-canonical-v4_0_0"));

        String netexQueue = MessageQueue.RULES.munge(EnturNetexValidatorRule.RULE_NAME);
        assertThat(netexQueue, equalTo("vaco-rules-netex-entur-v1_0_1"));
        assertThat(netexQueue, matchesPattern(versionedPattern));

        String fakeQueue = MessageQueue.RULES.munge("fake.rule.v1_2_3");
        assertThat(fakeQueue, matchesPattern("vaco-rules-fake-rule-v1_2_3"));
        assertThat(fakeQueue, matchesPattern(versionedPattern));
    }
}
