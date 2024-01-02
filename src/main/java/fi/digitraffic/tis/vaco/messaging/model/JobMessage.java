package fi.digitraffic.tis.vaco.messaging.model;

import fi.digitraffic.tis.vaco.queuehandler.model.Entry;

public interface JobMessage {
    // TODO: Should only carry entry id information for lighter message payload and to enforce reloading of entry
    //       just before processing.
    Entry entry();
}
