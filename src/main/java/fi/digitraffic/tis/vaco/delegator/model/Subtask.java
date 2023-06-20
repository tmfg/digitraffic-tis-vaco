package fi.digitraffic.tis.vaco.delegator.model;

public enum Subtask {

    VALIDATION(1),
    CONVERSION(2);

    public final int priority;

    Subtask(int priority) {
        this.priority = priority;
    }

}
