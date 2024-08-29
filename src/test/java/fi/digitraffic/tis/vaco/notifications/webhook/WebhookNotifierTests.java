package fi.digitraffic.tis.vaco.notifications.webhook;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableCompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableEntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.ImmutableNotificationResponse;
import fi.digitraffic.tis.vaco.http.model.NotificationResponse;
import fi.digitraffic.tis.vaco.notifications.model.EntryCompletePayload;
import fi.digitraffic.tis.vaco.notifications.model.Notification;
import fi.digitraffic.tis.vaco.notifications.model.NotificationType;
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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
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

    @Captor
    private ArgumentCaptor<byte[]> payload;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModules(new GuavaModule());
        webhookNotifier = new WebhookNotifier(
            companyRepository,
            httpClient,
            objectMapper,
            TestObjects.vacoProperties(),
            taskService,
            packagesService,
            new RecordMapper(objectMapper),
            contextRepository
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(companyRepository, httpClient, taskService, packagesService);
    }

    @Test
    void sendsNotificationToConfiguredWebhookNotificationUri(TestInfo testInfo) throws IOException {
        // given entry
        EntryRecord entry = ImmutableEntryRecord.of(456789L, NanoIdUtils.randomNanoId(), testInfo.getDisplayName(), "gtfs", "http://example.fi/WebhookNotifierTests", Constants.FINTRAFFIC_BUSINESS_ID)
            .withNotifications("do.not@expose");

        // and a company with configured webhook notification URI
        String notificationUri = "https://example.fi/webhooktest?xyzzy=685439fdjsilj3w8";
        CompanyRecord company = ImmutableCompanyRecord.of(996633L, Constants.FINTRAFFIC_BUSINESS_ID, false)
            .withNotificationWebhookUri(notificationUri);
        when(companyRepository.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID)).thenReturn(Optional.of(company));

        // and a task owned by entry
        ImmutableTask fakeTask = ImmutableTask.of("foo", 100);
        List<Task> tasks = List.of(fakeTask);
        when(taskService.findTasks(entry)).thenReturn(tasks);

        // with one produced package
        List<Package> packages = List.of(ImmutablePackage.of(fakeTask, "testpackage", "/path/to/blob"));
        when(packagesService.findAvailablePackages(fakeTask)).thenReturn(packages);

        // when webhook is sent
        NotificationResponse notificationResponse = ImmutableNotificationResponse.builder().build();
        when(httpClient.sendWebhook(any(String.class), any(byte[].class))).thenReturn(CompletableFuture.completedFuture(notificationResponse));

        // by the actual notifier trigger
        webhookNotifier.notifyEntryComplete(entry);

        // capture sent notification
        verify(httpClient).sendWebhook(eq(notificationUri), payload.capture());
        Notification sentNotification = objectMapper.readValue(payload.getValue(), Notification.class);
        // and verify/assert its contents
        assertThat(sentNotification.name(), equalTo(NotificationType.ENTRY_COMPLETE_V1.getTypeName()));
        assertThat(sentNotification.payload(), instanceOf(EntryCompletePayload.class));
        EntryCompletePayload payload = (EntryCompletePayload) sentNotification.payload();
        assertThat("Do not expose admin restricted notifications in webhook", payload.entry().notifications(), empty());
        assertThat(payload.packages(), equalTo(Map.of("foo", Map.of("testpackage", new Link("http://localhost:5173/v1/packages/" + entry.publicId() + "/foo/testpackage", RequestMethod.GET)))));
    }
}
