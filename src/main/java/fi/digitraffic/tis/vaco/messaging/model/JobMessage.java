package fi.digitraffic.tis.vaco.messaging.model;

import fi.digitraffic.tis.vaco.queuehandler.model.Entry;

public interface JobMessage {
    Entry entry();
}
