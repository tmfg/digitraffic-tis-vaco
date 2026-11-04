package fi.digitraffic.tis.vaco.notifications.webhook;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.guava.GuavaModule;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableCompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableEntryRecord;
import fi.digitraffic.tis.vaco.db.model.notifications.ImmutableSubscriptionRecord;
import fi.digitraffic.tis.vaco.db.model.notifications.SubscriptionRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.db.repositories.SubscriptionsRepository;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.ImmutableNotificationResponse;
import fi.digitraffic.tis.vaco.http.model.NotificationResponse;
import fi.digitraffic.tis.vaco.notifications.model.EntryCompletePayload;
import fi.digitraffic.tis.vaco.notifications.model.NotificationType;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookNotifierTests {

    private WebhookNotifier webhookNotifier;

    private ObjectMapper objectMapper;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private VacoHttpClient httpClient;

    @Mock
    private TaskService taskService;

    @Mock
    private PackagesService packagesService;

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private SubscriptionsRepository subscriptionsRepository;

    @Captor
    private ArgumentCaptor<byte[]> payload;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger notifierLogger;
    private Level originalNotifierLevel;

    @BeforeEach
    void setUp() {
        notifierLogger = (Logger) LoggerFactory.getLogger(WebhookNotifier.class);
        originalNotifierLevel = notifierLogger.getLevel();
        notifierLogger.setLevel(Level.INFO);
        logAppender = new ListAppender<>();
        logAppender.start();
        notifierLogger.addAppender(logAppender);

        objectMapper = JsonMapper.builder().addModule(new GuavaModule()).enable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
        webhookNotifier = new WebhookNotifier(
            companyRepository,
            httpClient,
            objectMapper,
            TestObjects.vacoProperties(),
            taskService,
            packagesService,
            new RecordMapper(objectMapper),
            contextRepository,
            subscriptionsRepository
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        notifierLogger.detachAppender(logAppender);
        notifierLogger.setLevel(originalNotifierLevel);
        verifyNoMoreInteractions(companyRepository, httpClient, taskService, packagesService, contextRepository, subscriptionsRepository);
    }

    @Test
    void sendsNotificationToConfiguredWebhookNotificationUri(TestInfo testInfo) throws IOException {
        // GIVEN a subscription with a webhook subscriber, and a successful receiver
        Arranged arranged = arrangeSubscriptionAnsweredWith(testInfo, ImmutableNotificationResponse.builder().statusCode(200).build());

        // WHEN the notifier is triggered
        webhookNotifier.notifyEntryComplete(arranged.entry());

        // VERIFY the success is logged as sent
        assertThat(notifierMessages(Level.INFO), equalTo(List.of(sentMessage(arranged.entry(), arranged.subscriber(), arranged.resource()))));
        assertThat(notifierMessages(Level.WARN), equalTo(List.of()));

        // VERIFY sent notification
        verify(httpClient).sendWebhook(eq(arranged.notificationUri()), payload.capture());
        JsonNode sentJson = objectMapper.readTree(payload.getValue());
        assertThat(sentJson.get("name").asString(), equalTo(NotificationType.ENTRY_COMPLETE_V1.getTypeName()));

        // Typed deserialization of the payload (validates structure and deserializability)
        EntryCompletePayload entryCompletePayload = objectMapper.treeToValue(sentJson.get("payload"), EntryCompletePayload.class);
        String expectedHref = "http://localhost:8080/api/v1/packages/" + arranged.entry().publicId() + "/foo/testpackage";
        assertThat("Sent event matches expected static values", entryCompletePayload.packages(),
            equalTo(Map.of("foo", Map.of("testpackage", new Link(expectedHref, RequestMethod.GET)))));

        // Security assertion: admin-restricted field must be absent from webhook JSON
        assertThat("Do not expose admin restricted notifications in webhook",
            sentJson.get("payload").get("entry").has("notifications"), is(false));
    }

    @Test
    void logsWarningWithStatusInsteadOfSentWhenReceiverAnsweredWithFailure(TestInfo testInfo) {
        // the receiver answered with a failure, which VacoHttpClient reports as a normally completed, undelivered response
        Arranged arranged = arrangeSubscriptionAnsweredWith(testInfo, ImmutableNotificationResponse.builder().statusCode(500).build());

        webhookNotifier.notifyEntryComplete(arranged.entry());

        verify(httpClient).sendWebhook(eq(arranged.notificationUri()), any(byte[].class));
        assertThat(notifierMessages(Level.WARN), equalTo(List.of(
            notDeliveredMessage(arranged.entry(), arranged.subscriber(), arranged.resource(), "HTTP status 500"))));
        assertNoSentOrDeliveryFailureAlarmMessages();
    }

    @Test
    void logsWarningWithoutStatusInsteadOfSentWhenNoResponseWasReceived(TestInfo testInfo) {
        // the request could not be built, which VacoHttpClient reports as an undelivered response without a status
        Arranged arranged = arrangeSubscriptionAnsweredWith(testInfo, ImmutableNotificationResponse.builder().build());

        webhookNotifier.notifyEntryComplete(arranged.entry());

        verify(httpClient).sendWebhook(eq(arranged.notificationUri()), any(byte[].class));
        assertThat(notifierMessages(Level.WARN), equalTo(List.of(
            notDeliveredMessage(arranged.entry(), arranged.subscriber(), arranged.resource(), "no response"))));
        assertNoSentOrDeliveryFailureAlarmMessages();
    }

    private record Arranged(EntryRecord entry, CompanyRecord subscriber, CompanyRecord resource, String notificationUri) {
    }

    /**
     * Arranges a resource company with an entry and one webhook subscriber, and stubs {@code httpClient} to answer with the given response.
     */
    private Arranged arrangeSubscriptionAnsweredWith(TestInfo testInfo, NotificationResponse notificationResponse) {
        // GIVEN a company as subscription resource
        CompanyRecord resource = ImmutableCompanyRecord.of(660033L, Constants.SOLITA_BUSINESS_ID, false);
        // and an entry relating to the resource
        EntryRecord entry = ImmutableEntryRecord.of(
                456789L,
                NanoIdUtils.randomNanoId(),
                testInfo.getDisplayName(),
                "gtfs",
                "http://example.fi/WebhookNotifierTests",
                resource.businessId())
            .withNotifications("do.not@expose");
        // and a company as subscriber with configured webhook notification URI
        String notificationUri = "https://example.fi/webhooktest?xyzzy=685439fdjsilj3w8";
        CompanyRecord subscriber = ImmutableCompanyRecord.of(996633L, Constants.FINTRAFFIC_BUSINESS_ID, false)
            .withNotificationWebhookUri(notificationUri);
        // and a task owned by entry
        ImmutableTask fakeTask = ImmutableTask.of("foo", 100);
        // and a subscription representing the link between all these
        SubscriptionRecord subscription = ImmutableSubscriptionRecord.builder()
            .id(333888777L)
            .publicId(NanoIdUtils.randomNanoId())
            .type(SubscriptionType.WEBHOOK)
            .subscriberId(subscriber.id())
            .resourceId(resource.id())
            .build();

        // entry's subscription resource is looked up, return it
        when(companyRepository.findByBusinessId(entry.businessId())).thenReturn(Optional.of(resource));
        // list all subscriptions for the resource
        when(subscriptionsRepository.findSubscriptionsForResource(SubscriptionType.WEBHOOK, resource)).thenReturn(List.of(subscription));
        // look up subscriber's details to access the WebHook notification URL
        when(companyRepository.findById(subscriber.id())).thenReturn(subscriber);
        // context is looked up for the entry in payload - there is none
        when(contextRepository.find(entry)).thenReturn(Optional.empty());
        // lookup entry's tasks to list produced packages
        when(taskService.findTasks(entry)).thenReturn(List.of(fakeTask));
        List<Package> packages = List.of(ImmutablePackage.of(fakeTask, "testpackage", "/path/to/blob"));
        when(packagesService.findAvailablePackages(fakeTask, entry.publicId())).thenReturn(packages);
        // the webhook is sent
        when(httpClient.sendWebhook(any(String.class), any(byte[].class))).thenReturn(CompletableFuture.completedFuture(notificationResponse));

        return new Arranged(entry, subscriber, resource, notificationUri);
    }

    private void assertNoSentOrDeliveryFailureAlarmMessages() {
        assertThat(notifierMessages(Level.INFO), equalTo(List.of()));
        // VacoHttpClient already logs "Webhook delivery failed", the notifier must not log it again for the alarm
        assertThat(logAppender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("Webhook delivery failed")), is(false));
    }

    private String notDeliveredMessage(EntryRecord entry, CompanyRecord subscriber, CompanyRecord resource, String outcome) {
        return "Webhook " + NotificationType.ENTRY_COMPLETE_V1.getTypeName() + " notification for entry " + entry.publicId()
            + " to subscriber " + subscriber.businessId() + " of resource " + resource.businessId() + " was not delivered (" + outcome + ")";
    }

    private String sentMessage(EntryRecord entry, CompanyRecord subscriber, CompanyRecord resource) {
        return "Webhook " + NotificationType.ENTRY_COMPLETE_V1.getTypeName() + " notification sent for entry " + entry.publicId()
            + " to subscriber " + subscriber.businessId() + " of resource " + resource.businessId();
    }

    private List<String> notifierMessages(Level level) {
        return logAppender.list.stream()
            .filter(e -> e.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }
}
