package fi.digitraffic.tis.utilities.model;

/**
 * Helper enum for providing type safety to service APIs when dealing with generic tracking of states.
 * Not meant to be persisted/exposed directly.
 */
public enum ProcessingState {
    START, UPDATE, COMPLETE
}
