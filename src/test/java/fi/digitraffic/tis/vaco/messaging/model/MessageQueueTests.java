package fi.digitraffic.tis.vaco.messaging.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
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
        assertThat(MessageQueue.RULES.munge("fake_rule_v1.2.3"), matchesPattern("^([a-z]+?-?)+v\\d+_\\d+_\\d$"));
    }
}
