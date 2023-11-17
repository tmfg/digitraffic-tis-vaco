package fi.digitraffic.tis.vaco.email.mapper;

import com.google.common.collect.Iterators;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.configuration.Email;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.email.model.Message;
import fi.digitraffic.tis.vaco.email.model.Recipients;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Component
public class MessageMapper {

    private final Email emailProperties;

    public MessageMapper(VacoProperties vacoProperties) {
        this.emailProperties = Objects.requireNonNull(vacoProperties.email());
    }

    /**
     * Converts given recipients and message to multiple AWS SES SendEmailRequests based on the requirements of the
     * underlying client library/APIs, such as limit maximum number of recipients to 50 per email.
     *
     * @param recipients All recipients
     * @param message The message
     * @return List of generated requests
     */
    public List<SendEmailRequest> toSendEmailRequests(Recipients recipients, Message message) {
        return toDestinations(recipients)
            .map(r -> {
                SendEmailRequest.Builder builder = SendEmailRequest.builder();

                builder.destination(toDestination(r))
                    .message(toMessage(message))
                    .source(emailProperties.from());

                builder = mutateListField(builder, resolveReplyTo(), SendEmailRequest.Builder::replyToAddresses);

                return builder.build();
            }).toList();
    }

    private List<String> resolveReplyTo() {
        List<String> replyTo = emailProperties.replyTo();
        return Objects.requireNonNullElseGet(replyTo, () -> List.of(emailProperties.from()));
    }

    public software.amazon.awssdk.services.ses.model.Message toMessage(Message message) {
        software.amazon.awssdk.services.ses.model.Message.Builder builder = software.amazon.awssdk.services.ses.model.Message.builder();

        builder.subject(subject -> subject.data(message.subject()))
            .body(body -> body.html(content -> content.data(message.body())));
        return builder.build();
    }

    /**
     * Split recipients to groups of 50, ignoring the type (cc, bcc) of recipient.
     *
     * @param recipients All recipients
     * @return Partitioned stream with max number of allowed recipients per partition.
     */
    private Stream<List<Recipient>> toDestinations(Recipients recipients) {
        Streams.Chain<Recipient> allRecipients = Streams.concat(
            Streams.map(recipients.to(), r -> new Recipient(r, RecipientType.TO)).stream(),
            Streams.map(recipients.cc(), r -> new Recipient(r, RecipientType.CC)).stream(),
            Streams.map(recipients.bcc(), r -> new Recipient(r, RecipientType.BCC)).stream()
        );

        Iterator<List<Recipient>> recipientsIterator = Iterators.partition(allRecipients.stream().iterator(), 50);

        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(recipientsIterator, Spliterator.ORDERED),
            false);
    }

    private Destination toDestination(List<Recipient> recipientPartition) {
        Destination.Builder builder = Destination.builder();

        Map<RecipientType, List<Recipient>> byType = Streams.groupBy(recipientPartition, Recipient::recipientType);
        for (Map.Entry<RecipientType, List<Recipient>> entry : byType.entrySet()) {
            List<String> recipients = Streams.collect(entry.getValue(), r -> r.address);
            builder = switch (entry.getKey()) {
                case TO -> mutateListField(builder, recipients, Destination.Builder::toAddresses);
                case CC -> mutateListField(builder, recipients, Destination.Builder::ccAddresses);
                case BCC -> mutateListField(builder, recipients, Destination.Builder::bccAddresses);
            };
        }

        return builder.build();
    }

    private static <T, B> B mutateListField(B builder, List<T> things, BiFunction<B, List<T>, B> mutator) {
        if (things != null && !things.isEmpty()) {
            builder = mutator.apply(builder, things);
        }
        return builder;
    }

    private enum RecipientType {
        TO, CC, BCC
    }

    private record Recipient(String address, RecipientType recipientType) {
    }
}
