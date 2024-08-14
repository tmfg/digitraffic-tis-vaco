package fi.digitraffic.tis.vaco.notifications.email;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.AwsIntegrationTestBase;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.configuration.Email;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.crypt.EncryptionService;
import fi.digitraffic.tis.vaco.notifications.email.mapper.MessageMapper;
import fi.digitraffic.tis.vaco.email.model.ImmutableMessage;
import fi.digitraffic.tis.vaco.email.model.ImmutableRecipients;
import fi.digitraffic.tis.vaco.featureflags.FeatureFlagsService;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.ui.AdminToolsRepository;
import fi.digitraffic.tis.vaco.ui.model.ImmutableCompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken;
import fi.digitraffic.tis.vaco.ui.model.MagicToken;
import io.burt.jmespath.jackson.JacksonRuntime;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EmailNotifierTests extends AwsIntegrationTestBase {

    private EmailNotifier emailNotifier;
    private ObjectMapper objectMapper;
    private JacksonRuntime jmesPath;
    private VacoProperties vacoProperties;

    @Mock
    private CompanyHierarchyService companyHierarchyService;
    @Mock
    private FeatureFlagsService featureFlagsService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private AdminToolsRepository adminToolsRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jmesPath = new JacksonRuntime();
        vacoProperties = TestObjects.vacoProperties(null, null, new Email("king@commonwealth", null), null, null, null);

        emailNotifier = new EmailNotifier(
            vacoProperties,
            new MessageMapper(vacoProperties),
            sesClient,
            companyHierarchyService,
            featureFlagsService,
            encryptionService,
            adminToolsRepository);
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        // clear all received messages
        localstack.execInContainer("curl", "-X", "DELETE", "localhost.localstack.cloud:4566/_aws/ses");

        verifyNoMoreInteractions(companyHierarchyService, featureFlagsService, encryptionService, adminToolsRepository);
    }

    @BeforeAll
    static void beforeAll() {
        sesClient.verifyEmailAddress(verify -> verify.emailAddress("king@commonwealth"));
    }

    @Test
    void sendGivenMessageToSpecifiedAddresses() throws IOException, InterruptedException {
        emailNotifier.sendMessage(
            ImmutableRecipients.builder()
                .addTo("subjects@commonwealth")
                .addCc("church@commonwealth")
                .addBcc("prince@commonwealth")
                .build(),
            ImmutableMessage.builder()
                .subject("My loyal subjects")
                .body("I hereby decree that you are all free from my reign.")
                .build());

        JsonNode messages = readReceivedMessages();

        assertAll(
            messagesSent(messages, 1),
            () -> assertThat(
                path(messages, "messages[0].Source").textValue(),
                equalTo("king@commonwealth")),
            () -> assertThat(
                list(path(messages, "messages[].Destination[].ToAddresses[]"), JsonNode::textValue),
                equalTo(List.of("subjects@commonwealth"))),
            () -> assertThat(
                path(messages, "messages[0].Body.html_part").textValue(),
                equalTo("I hereby decree that you are all free from my reign."))
        );
    }

    @Test
    void splitsEmailWithMoreThan50RecipientToMultipleEmails() throws IOException, InterruptedException {
        List<String> lackeys = IntStream.range(0, 120).mapToObj(i -> "lackey-" + i + "@commonwealth").toList();
        List<String> militia = IntStream.range(0, 70).mapToObj(i -> "militia-" + i + "@commonwealth").toList();

        emailNotifier.sendMessage(
            ImmutableRecipients.builder()
                .addAllCc(lackeys)
                .addAllBcc(militia)
                .build(),
            ImmutableMessage.builder()
                .subject("To whom it may concern")
                .body("My son, Prince Rechibald, is now the supreme monarch of this fine commonwealth.")
                .build());

        JsonNode messages = readReceivedMessages();

        assertAll(
            messagesSent(messages, 4),
            () -> assertThat(
                list(path(messages, "messages[*].Source"), JsonNode::textValue),
                equalTo(Collections.nCopies(4, "king@commonwealth"))),
            () -> assertThat(
                list(path(messages, "messages[*].Body.html_part"), JsonNode::textValue),
                equalTo(Collections.nCopies(4, "My son, Prince Rechibald, is now the supreme monarch of this fine commonwealth."))),
            // all cc and bcc are referenced
            () -> assertThat(
                list(path(messages, "messages[*].Destination[].CcAddresses[]"), JsonNode::textValue),
                equalTo(lackeys)),
            () -> assertThat(
                list(path(messages, "messages[*].Destination[].BccAddresses[]"), JsonNode::textValue),
                equalTo(militia)),
            // test splits
            () -> assertThat(
                list(path(messages, "messages[0].Destination.CcAddresses"), JsonNode::textValue),
                equalTo(lackeys.subList(0, 50))),
            () -> assertThat(
                list(path(messages, "messages[1].Destination.CcAddresses"), JsonNode::textValue),
                equalTo(lackeys.subList(50, 100))),
            () -> assertThat(
                list(path(messages, "messages[2].Destination.CcAddresses"), JsonNode::textValue),
                equalTo(lackeys.subList(100, 120))),
            () -> assertThat(
                list(path(messages, "messages[2].Destination.BccAddresses"), JsonNode::textValue),
                equalTo(militia.subList(0, 30))),
            () -> assertThat(
                list(path(messages, "messages[3].Destination.BccAddresses"), JsonNode::textValue),
                equalTo(militia.subList(30, 70)))
        );
    }

    @Test
    void weeklyStatusEmailIsSentToRelatedCompanyContacts() throws IOException, InterruptedException {
        Company org = TestObjects.aCompany().addContactEmails("organ@izati.on").build();
        ImmutableCompanyLatestEntry entry = ImmutableCompanyLatestEntry.builder()
            .companyName("some company")
            .feedName("testName")
            .format("gtfs")
            .url("https://testfile")
            .publicId(NanoIdUtils.randomNanoId())
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID)
            .build();

        BDDMockito.given(featureFlagsService.isFeatureFlagEnabled("emails.feedStatusEmail")).willReturn(true);
        BDDMockito.given(adminToolsRepository.getDataDeliveryOverview(Set.of(org.businessId()))).willReturn(List.of(entry));
        BDDMockito.given(encryptionService.encrypt(any(MagicToken.class))).willAnswer(a -> "magic/link/for/" + ((ImmutableMagicToken) a.getArgument(0)).token());

        emailNotifier.sendFeedStatusEmail(org);
        JsonNode messages = readReceivedMessages();

        assertAll(
            messagesSent(messages, 1),
            () -> assertThat(
                list(path(messages, "messages[*].Destination[].CcAddresses[]"), JsonNode::textValue),
                equalTo(org.contactEmails()))
            );
        String message = list(path(messages, "messages[].Body.html_part"), JsonNode::textValue).get(0);
        assertThat(message, containsString("<title>NAP:iin ilmoittamienne rajapintojen tilanneraportti</title>"));

        assertThat(
            "Generated email should contain VACO badge",
            message,
            containsString("<img src=\"" + vacoProperties.baseUrl() + "/api/badge/" + entry.publicId() + "\" />"));
        assertThat(
            "Generated email should contain magic link to UI",
            message,
            containsString("<a href=\"http://localhost:5173/ui/data/" + entry.publicId() + "?magic=magic/link/for/" + entry.publicId() + "\">Tarkastele VACO UI:ssa</a>"));
    }

    @Test
    void willNotSendWeeklyStatusEmailIfFeatureFlagIsDisabled() throws IOException, InterruptedException {
        Company org = TestObjects.aCompany().addContactEmails("organ@izati.on").build();

        BDDMockito.given(featureFlagsService.isFeatureFlagEnabled("emails.feedStatusEmail")).willReturn(false);

        emailNotifier.sendFeedStatusEmail(org);

        assertAll(messagesSent(readReceivedMessages(), 0));
    }

    @Test
    void willNotSendEntryCompleteEmailIfFeatureFlagIsDisabled() throws IOException, InterruptedException {
        ImmutableEntry entry = TestObjects.anEntry("gtfs").build();

        BDDMockito.given(featureFlagsService.isFeatureFlagEnabled("emails.entryCompleteEmail")).willReturn(false);

        emailNotifier.notifyEntryComplete(entry);

        assertAll(messagesSent(readReceivedMessages(), 0));
    }

    @NotNull
    private Executable messagesSent(JsonNode messages, int count) {
        return () -> assertThat(
            path(messages, "length(messages)").intValue(),
            equalTo(count));
    }

    private List<? extends String> list(JsonNode node, Function<JsonNode, String> mapper) {
        return Streams.collect(jmesPath.toList(node), mapper);
    }

    private JsonNode path(JsonNode root, String expression) {
        return jmesPath.compile(expression).search(root);
    }

    private JsonNode readReceivedMessages() throws IOException, InterruptedException {
        String messages = localstack.execInContainer("curl","--silent","localhost.localstack.cloud:4566/_aws/ses").getStdout();
        return objectMapper.readTree(messages);
    }
}
