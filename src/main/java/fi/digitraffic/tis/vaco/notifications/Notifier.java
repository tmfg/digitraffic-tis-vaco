package fi.digitraffic.tis.vaco.notifications;

import fi.digitraffic.tis.vaco.db.model.EntryRecord;

/**
 * Notifiers allow reacting to system events in various ways.
 */
public interface Notifier {

    void notifyEntryComplete(EntryRecord entry);

}
