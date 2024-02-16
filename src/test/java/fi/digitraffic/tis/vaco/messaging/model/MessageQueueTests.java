package fi.digitraffic.tis.vaco.messaging.model;

import fi.digitraffic.tis.vaco.rules.RuleName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

class MessageQueueTests {

    public static final String QUEUE_NAME = "^([a-zA-Z0-9]+-?)+$";

    @Test
    void queueNamesMatchDesiredPattern() {
        for (MessageQueue mq : MessageQueue.values()) {
            String queueName = mq.getQueueName();
            // this test is only for non-patternet queue names, see test below
            if (!queueName.contains("{")) {
                assertThat(queueName, matchesPattern(QUEUE_NAME));
            }
        }
    }

    @Test
    void ruleQueueMatchesDesiredPattern() {
        String gtfsQueue = MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL);

        assertThat(gtfsQueue, equalTo("rules-processing-gtfs-canonical"));

        String netexQueue = MessageQueue.RULE_PROCESSING.munge(RuleName.NETEX_ENTUR);
        assertThat(netexQueue, equalTo("rules-processing-netex-entur"));
        assertThat(netexQueue, matchesPattern(QUEUE_NAME));

        String fakeQueue = MessageQueue.RULE_PROCESSING.munge("fake.rule");
        assertThat(fakeQueue, matchesPattern("rules-processing-fake-rule"));
        assertThat(fakeQueue, matchesPattern(QUEUE_NAME));
    }
}
