package fi.digitraffic.tis.vaco.messaging.model;

import fi.digitraffic.tis.vaco.rules.RuleName;
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
        String gtfsQueue = MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL_4_0_0);

        assertThat(gtfsQueue, equalTo("rules-processing-gtfs-canonical-v4_0_0"));

        String netexQueue = MessageQueue.RULE_PROCESSING.munge(RuleName.NETEX_ENTUR_1_0_1);
        assertThat(netexQueue, equalTo("rules-processing-netex-entur-v1_0_1"));
        assertThat(netexQueue, matchesPattern(versionedPattern));

        String fakeQueue = MessageQueue.RULE_PROCESSING.munge("fake.rule.v1_2_3");
        assertThat(fakeQueue, matchesPattern("rules-processing-fake-rule-v1_2_3"));
        assertThat(fakeQueue, matchesPattern(versionedPattern));
    }
}
