package fi.digitraffic.tis.vaco.email.mapper;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.Email;
import fi.digitraffic.tis.vaco.email.model.ImmutableRecipients;
import fi.digitraffic.tis.vaco.email.model.ImmutableMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;

class MessageMapperTests {

    private MessageMapper mapper;
    private Email email;

    @BeforeEach
    void setUp() {
        email = new Email("from@example.fi", List.of("replyTo+a@example.fi", "replyTo+b@example.fi"));
        mapper = new MessageMapper(TestObjects.vacoProperties(null, null, email, null));
    }

    @Test
    void mapsMessageToSpringsSimpleMailMessages() {
        ImmutableRecipients addresses = ImmutableRecipients.builder()
            .addTo("to+a@example.fi", "to+b@example.fi")
            .addCc("cc+a@example.fi", "cc+b@example.fi")
            .addBcc("bcc+a@example.fi", "bcc+b@example.fi")
            .build();
        ImmutableMessage msg = ImmutableMessage.builder()
            .subject("Hello!")
            .body("<h1>awesome</h1>")
            .build();

        List<SendEmailRequest> reqs = mapper.toSendEmailRequests(addresses, msg);

        assertThat(reqs.size(), equalTo(1));

        reqs.forEach(req -> {
            assertAll(
                () -> assertThat(req.source(), equalTo(email.from())),
                () -> assertThat(req.replyToAddresses(), equalTo(email.replyTo()))
            );

            Destination destination = req.destination();
            assertAll(
                () -> assertThat(destination.toAddresses(), equalTo(addresses.to())),
                () -> assertThat(destination.ccAddresses(), equalTo(addresses.cc())),
                () -> assertThat(destination.bccAddresses(), equalTo(addresses.bcc()))
            );
            Message message = req.message();
            assertAll(
                () -> assertThat(message.subject().data(), equalTo(msg.subject())),
                () -> assertThat(message.body().html().data(), equalTo(msg.body()))
            );
        });
    }

    @Test
    void convertsRequestWithNoRecipientsToEmptyList() {
        List<SendEmailRequest> reqs = mapper.toSendEmailRequests(ImmutableRecipients.builder().build(), ImmutableMessage.of("sub", "msg"));

        assertThat(reqs.size(), equalTo(0));
    }
}
