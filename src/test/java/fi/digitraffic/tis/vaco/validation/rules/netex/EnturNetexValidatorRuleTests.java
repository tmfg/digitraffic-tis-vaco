package fi.digitraffic.tis.vaco.validation.rules.netex;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.rules.ValidatorRule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Entur's test files are from https://enturas.atlassian.net/wiki/spaces/PUBLIC/pages/728891505/NeTEx+examples+catalogue
 */
@ExtendWith(MockitoExtension.class)
class EnturNetexValidatorRuleTests {

    /* General TODO and implementation note:
     * NeTEx is a complex format and related work is at the time of writing in progress on Fintraffic's side. Therefore
     * this validator cannot be tested in its entirety right now; we have to return to this eventually once we have more
     * experience with the format and Fintraffic's own feeds.
     *
     * It should be noted that a common testing base class is probably reasonable for all ValidatorRules, as there's
     * lots of shared code in the mocking infrastructure of these test classes.
     */

    private static final Long MOCK_PHASE_ID = 4009763L;

    private ValidatorRule rule;

    private ObjectMapper objectMapper;
    @Mock
    private ErrorHandlerService errorHandlerService;
    @Mock
    private RulesetRepository rulesetRepository;
    private ImmutableEntry queueEntry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rule = new EnturNetexValidatorRule(rulesetRepository, errorHandlerService, objectMapper);
        queueEntry = TestObjects.anEntry("NeTEx").build();
    }

    @Test
    void validatesEntursExampleFilesWithoutErrors() throws URISyntaxException {
        String testFile = "public/testfiles/entur-netex.zip";
        ValidationReport report = rule.execute(queueEntry, Optional.empty(), forInput(testFile)).join();

        assertThat(report.errors().size(), equalTo(0));
    }

    @NotNull
    private ImmutablePhaseData<FileReferences> forInput(String testFile) throws URISyntaxException {
        return ImmutablePhaseData.<FileReferences>builder()
            .phase(TestObjects.aPhase()
                .id(MOCK_PHASE_ID)
                .name(ValidationService.EXECUTION_PHASE)
                .build())
            .payload(ImmutableFileReferences.of(testResource(testFile)))
            .build();
    }

    private Path testResource(String resourceName) throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource).toURI());
    }

}
