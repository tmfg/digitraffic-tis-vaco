package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;

public interface ResultProcessor {
    boolean processResults(ResultMessage resultMessage, Entry entry, Task task);
}
