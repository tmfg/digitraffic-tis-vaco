package fi.digitraffic.tis.vaco.process;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.netex.ImmutableEnturNetexValidatorConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;

class TaskServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private TaskService taskService;

    @Autowired
    private EntryService entryService;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private QueueHandlerService queueHandlerService;

    @Test
    void doesNotAddUnnecessaryConversionTasksForNetexValidation() {
        Entry e = ImmutableEntry.builder()
            .name("name")
            .publicId("huuhaa")
            .url("someurl")
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID)
            .format("netex")
            .addValidations(ImmutableValidationInput.builder()
                .name(RuleName.NETEX_ENTUR)
                .config(ImmutableEnturNetexValidatorConfiguration.builder()
                    .codespace("FIN")
                    .maximumErrors(1000)
                    .build())
                .build())
            .build();
        Optional<Entry> saved = queueHandlerService.processQueueEntry(e);
        Optional<EntryRecord> r = entryRepository.findByPublicId(saved.get().publicId());
        List<Task> t = taskService.resolveTasks(r.get());

        assertThat(t.size(), equalTo(2));
        assertTask(t.get(0), "prepare.download", 100);
        assertTask(t.get(1), "netex.entur", 200);
    }

    private void assertTask(Task task, String name, int priority) {
        assertAll(
            () -> assertThat(task.name(), equalTo(name)),
            () -> assertThat(task.priority(), equalTo(priority))
        );
    }
}
