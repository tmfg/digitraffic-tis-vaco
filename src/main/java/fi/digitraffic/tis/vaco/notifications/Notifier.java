package fi.digitraffic.tis.vaco.notifications;

import fi.digitraffic.tis.vaco.queuehandler.model.Entry;

/**
 * Notifiers allow reacting to system events in various ways.
 */
public interface Notifier {

    void notifyEntryComplete(Entry entry);

}
