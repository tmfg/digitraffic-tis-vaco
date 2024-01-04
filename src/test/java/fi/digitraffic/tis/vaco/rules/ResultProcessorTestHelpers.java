package fi.digitraffic.tis.vaco.rules;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ResultProcessorTestHelpers {

    public static Entry entryWithTask(Function<Entry, Task> taskCreator) {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        ImmutableEntry entry = entryBuilder.build();
        entry = entry.withTasks(taskCreator.apply(entry));
        return entry;
    }

    @NotNull
    public static ResultMessage asResultMessage(VacoProperties vacoProperties,
                                                String ruleName, Entry entry,
                                                Map<String, ? extends List<String>> uploadedFiles) {
        return ImmutableResultMessage.builder()
            .ruleName(ruleName)
            .entryId(entry.publicId())
            .taskId(entry.tasks().get(0).id())
            .inputs("s3://" + vacoProperties.s3ProcessingBucket() + "/inputs")
            .outputs("s3://" + vacoProperties.s3ProcessingBucket() + "/outputs")
            .uploadedFiles(uploadedFiles)
            .build();
    }

}
